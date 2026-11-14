package app.acervatim.domain.usecase

import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCatchUpState
import app.acervatim.domain.model.absentFrom
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.catchUpSince
import app.acervatim.domain.model.isCatchUpDue
import app.acervatim.domain.model.isCatchUpTrackable
import app.acervatim.domain.model.mergeCatchUpTomes
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesCatchUpStore
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * **Rattrapage** des tomes d'une série manga en cours (AD3, cf. `spec-macro-import-serie-manga.md`) :
 * l'import a créé tous les tomes connus à son instant ; ceux qui paraissent ensuite sont **proposés**
 * à l'ouverture de la série, jamais créés d'office.
 *
 * Deux sources, mêmes règles de dédoublonnage :
 * - pour tout utilisateur connecté, `edition-mapping?since=` sur l'édition de la série (titre,
 *   éditeur, mention) — au plus une fois tous les [app.acervatim.domain.model.CATCH_UP_INTERVAL_DAYS]
 *   jours, la BnF ne cataloguant un tome que des semaines après sa sortie ;
 * - en premium, les nouveautés calculées par le job serveur hebdomadaire (`series-updates`) pour le
 *   nœud de cette série, lues au même moment.
 *
 * Aucun appel réseau hors session. Une vérification ratée (réseau) ou incomplète
 * (`EditionMapping.complete == false`) n'avance pas la date : la même fenêtre sera retentée.
 */
class SeriesCatchUpUseCase @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val recordRepository: RecordRepository,
    private val externalSearch: ExternalSearchRepository,
    private val store: SeriesCatchUpStore,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val importSeries: ImportScannedSeriesUseCase,
    private val updatesCache: SeriesUpdatesCache,
    private val clock: Clock
) {

    /**
     * Vérifie la série si elle est éligible et due, puis renvoie les nouveaux tomes **à proposer** :
     * en attente, absents de la série, bannière non écartée. Liste vide hors session, pour une série
     * non suivie, ou quand il n'y a rien. Ne lève jamais : le détail d'une série ne doit pas échouer
     * pour une vérification d'arrière-plan.
     */
    suspend fun check(seriesId: String): List<EditionTome> {
        val userId = (authRepository.authState.value as? AuthState.SignedIn)?.userId ?: return emptyList()
        bindTo(userId)
        val series = seriesRepository.observeById(seriesId).first() ?: return emptyList()
        if (!series.isCatchUpTrackable()) return emptyList()

        val volumes = recordRepository.observeBySeriesId(seriesId).first()
        val today = today()
        val stored = store.read(seriesId)

        var state = stored
        if (isCatchUpDue(state, series.createdOn(), today)) {
            state = verify(series, state, today, userId)
        }
        // Les tomes ajoutés entre-temps à la main ou par scan sortent de l'attente.
        state = state.copy(pending = state.pending.absentFrom(volumes))
        if (state != stored) store.write(seriesId, state)

        return if (state.dismissedOn != null) emptyList() else state.pending
    }

    /** « Plus tard » : masque la bannière jusqu'à la prochaine vérification réussie. */
    suspend fun dismiss(seriesId: String) {
        val state = store.read(seriesId)
        store.write(seriesId, state.copy(dismissedOn = today()))
    }

    /**
     * Crée en wishlist les tomes en attente encore absents de la série, puis vide l'attente.
     * `null` si la série n'existe plus.
     */
    suspend fun addToWishlist(seriesId: String): ImportResult? {
        val series = seriesRepository.observeById(seriesId).first() ?: return null
        val state = store.read(seriesId)
        val volumes = recordRepository.observeBySeriesId(seriesId).first()
        val tomes = state.pending.absentFrom(volumes)
        val result = if (tomes.isEmpty()) {
            ImportResult(seriesId = seriesId, createdVolumes = 0, skippedExisting = 0, partial = false)
        } else {
            importSeries.importNewTomes(series, tomes)
        }
        store.write(seriesId, state.copy(pending = emptyList(), dismissedOn = null))
        return result
    }

    /**
     * Interroge les sources et fusionne ce qu'elles trouvent à l'attente. La date n'avance que si
     * l'énumération locale a répondu **et** s'est dite complète : les nouveautés premium seules ne
     * suffisent pas, elles ne couvrent que les séries synchronisées.
     */
    /**
     * L'état stocké a-t-il été calculé pour [userId] ? Sinon (autre compte après une session expirée,
     * ou état d'avant ce rattachement), on l'oublie : il ne doit pas fuiter d'un compte à l'autre. La
     * déconnexion explicite, elle, vide déjà tout ([ClearSeriesCatchUpUseCase]).
     */
    private suspend fun bindTo(userId: String) {
        if (store.owner() == userId) return
        updatesCache.invalidate()
        store.clearAll(owner = userId)
    }

    private suspend fun verify(
        series: Series,
        state: SeriesCatchUpState,
        today: LocalDate,
        userId: String
    ): SeriesCatchUpState {
        val mapping = externalSearch.editionMapping(
            titleFr = series.title,
            edition = series.edition,
            publisher = series.publisherFr,
            mangaId = series.mangaId,
            since = catchUpSince(state, series.createdOn())
        ).getOrNull()

        val found = mapping?.tomes.orEmpty() + premiumUpdates(series.id, userId)
        val advanced = mapping != null && mapping.complete
        return state.copy(
            lastCheckedOn = if (advanced) today else state.lastCheckedOn,
            pending = mergeCatchUpTomes(state.pending, found),
            // Une vérification réussie rouvre la bannière, même sur des tomes déjà écartés : c'est
            // la promesse du « Plus tard ».
            dismissedOn = if (advanced) null else state.dismissedOn
        )
    }

    /**
     * Nouveautés du job serveur pour **cette** série : la ligne dont le `nodeId` est le `remoteId`
     * de la série, lu une seule fois. Une série jamais synchronisée n'a pas de nœud, donc rien à lire.
     * Hors premium, le serveur répond `402` : on ne l'appelle donc pas sans un premium connu, et
     * toute erreur est ignorée — le rattrapage local suffit. La réponse, qui couvre tout le compte,
     * vient du cache de session ([SeriesUpdatesCache]) : pas un téléchargement par série ouverte.
     */
    private suspend fun premiumUpdates(seriesId: String, userId: String): List<EditionTome> {
        val account = accountRepository.accountInfo.first()
        val isPremium = (account as? AccountInfo.Known)?.profile?.premium?.isPremium == true
        if (!isPremium) return emptyList()
        val remoteId = seriesRepository.remoteIdOf(seriesId) ?: return emptyList()
        val updates = updatesCache.get(userId) ?: return emptyList()
        return updates.firstOrNull { it.nodeId == remoteId }?.newTomes.orEmpty()
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    // Même zone que [today] (l'horloge de l'app, UTC) : sinon la création et la vérification ne
    // seraient pas datées sur le même calendrier et l'intervalle de 7 jours glisserait d'un jour.
    private fun Series.createdOn(): LocalDate = createdAt.atZone(clock.zone).toLocalDate()
}
