package app.acervatim.ui.sharing.received

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.matches
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.model.toEntriesByPrimaryArtist
import app.acervatim.domain.model.toEntriesFlattenedByArtist
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionContent
import app.acervatim.domain.repository.SharedCollectionRepository
import app.acervatim.domain.repository.SettingsRepository
import app.acervatim.domain.repository.SharingRepository
import app.acervatim.domain.usecase.CollectionStats
import app.acervatim.ui.list.CollectionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** État structurel de la vue partagée — distinct du contenu, qui est un [CollectionUiState]. */
sealed interface SharedCollectionContentState {
    data object Loading : SharedCollectionContentState

    /** Réseau ou serveur : réessayable, contrairement à [Gone] et [Suspended]. */
    data object Failed : SharedCollectionContentState

    /**
     * Le partage a été révoqué, ou le membre éjecté : l'accès est coupé net (`404`). État terminal
     * côté app — rien à réessayer, et rien à purger non plus (aucune donnée locale n'existe).
     */
    data object Gone : SharedCollectionContentState

    /**
     * Le **propriétaire** a perdu son premium (`402`). C'est une **suspension**, pas une suppression :
     * l'accès revient s'il repaie. Le dire ainsi évite de faire croire à une perte définitive.
     */
    data object Suspended : SharedCollectionContentState

    /** Chargé. Le rendu est porté par [SharedCollectionUiState.collection]. */
    data object Ready : SharedCollectionContentState
}

data class SharedCollectionUiState(
    val collectionName: String? = null,
    /** Ce que le propriétaire expose. Affiché en permanence, sinon la vue paraît incomplète. */
    val statuses: Set<ItemStatus> = emptySet(),
    val listMode: ListMode = ListMode.FLAT,
    val supportsSerial: Boolean = false,
    val query: String = "",
    val state: SharedCollectionContentState = SharedCollectionContentState.Loading,
    /** **Le même** état de liste que pour ses propres collections : même rendu, mêmes sections. */
    val collection: CollectionUiState = CollectionUiState.Loading,
    val error: SharingFailure? = null
)

/**
 * Consultation **en lecture seule** d'une collection reçue en partage.
 *
 * Ce ViewModel ne construit pas un rendu à lui : il produit un [CollectionUiState], celui-là même que
 * `CollectionViewModel` produit pour les collections locales, et l'écran le donne à lire au corps de
 * liste partagé. Sections par lettre, bascule plate/série, états vides, et le tri quand il viendra :
 * tout est hérité au lieu d'être réécrit.
 *
 * La différence est la **source**. Une collection partagée n'a pas de table : elle est chargée d'un
 * bloc depuis le réseau, jamais persistée, et la recherche s'applique en mémoire via le même prédicat
 * que le `WHERE` SQL local ([matches]).
 */
@HiltViewModel
class SharedCollectionViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val sharedRepository: SharedCollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val collectionId: String = savedStateHandle.get<String>(COLLECTION_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow(SharedCollectionUiState())
    val uiState: StateFlow<SharedCollectionUiState> = _uiState.asStateFlow()

    /** Contenu brut, tel que chargé. La recherche et la bascule de mode retravaillent celui-ci. */
    private var content: SharedCollectionContent = SharedCollectionContent(emptyList())
    private var collectionType: CollectionType = CollectionType.VINYL

    /**
     * Réglage « un disque par artiste crédité ». C'est une préférence **d'affichage de
     * l'utilisateur**, pas une propriété de la collection : elle n'a aucune raison de s'arrêter à la
     * frontière du partage. Lu une fois au chargement — le rebuild est synchrone.
     */
    private var duplicateByArtist: Boolean = false

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        rebuild()
    }

    fun onClearQuery() = onQueryChange("")

    fun onListModeChange(mode: ListMode) {
        _uiState.update { it.copy(listMode = mode) }
        rebuild()
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun load() {
        _uiState.update {
            it.copy(
                state = SharedCollectionContentState.Loading,
                collection = CollectionUiState.Loading
            )
        }
        viewModelScope.launch {
            // On repasse par la liste des partages reçus plutôt que par `GET /v1/collections/:id` :
            // elle porte le nom, le type et les statuts accordés, et son absence dit exactement ce
            // qu'il faut savoir — l'accès n'existe plus.
            sharingRepository.receivedShares()
                .onSuccess { shares ->
                    val entries = shares.flatMap { it.collections }
                        .filter { it.collectionId == collectionId }
                    if (entries.isEmpty()) {
                        _uiState.update { it.copy(state = SharedCollectionContentState.Gone) }
                        return@onSuccess
                    }
                    val entry = entries.first()
                    collectionType = entry.type ?: CollectionType.VINYL
                    _uiState.update {
                        it.copy(
                            collectionName = entry.name,
                            // Plusieurs partages de la même collection : on voit l'**union** de ce que
                            // chacun accorde, exactement comme le calcule le serveur.
                            statuses = entries.flatMap { e -> e.statuses }.toSet(),
                            supportsSerial = collectionType.supportsSerial,
                            listMode = collectionType.defaultListMode
                        )
                    }
                    fetch()
                }
                .onFailure { error -> _uiState.update { it.copy(state = error.toState()) } }
        }
    }

    private suspend fun fetch() {
        duplicateByArtist = settingsRepository.observeDuplicateAlbumsPerArtist().first()
        sharedRepository.load(collectionId, collectionType)
            .onSuccess { loaded ->
                content = loaded
                _uiState.update { it.copy(state = SharedCollectionContentState.Ready) }
                rebuild()
            }
            .onFailure { error -> _uiState.update { it.copy(state = error.toState()) } }
    }

    /**
     * Reconstruit l'état de liste depuis le contenu déjà chargé.
     *
     * Purement en mémoire : changer la recherche ou le mode ne retourne pas au réseau. C'est la
     * contrepartie du chargement d'un bloc — et ce qui permet à la recherche de porter sur la
     * collection entière plutôt que sur la page affichée.
     */
    private fun rebuild() {
        val state = _uiState.value
        if (state.state != SharedCollectionContentState.Ready) return

        val filter = RecordFilter(
            query = state.query,
            collectionType = collectionType,
            listMode = state.listMode
        )
        val now = clock.instant()
        val visible = content.records.filter { filter.matches(it, now) }

        _uiState.update {
            it.copy(
                collection = CollectionUiState.from(
                    filter = filter,
                    entries = if (duplicateByArtist) {
                        visible.toEntriesFlattenedByArtist()
                    } else {
                        visible.toEntriesByPrimaryArtist()
                    },
                    series = seriesWithCounts(state.query),
                    stats = stats(),
                    // Une collection reçue n'a pas de préférence d'affichage : on reprend la
                    // densité historique de chaque vue — liste à plat, grille pour les séries.
                    records = emptyList(),
                    displayMode = if (state.listMode == ListMode.SERIAL) {
                        DisplayMode.GRID
                    } else {
                        DisplayMode.LIST
                    },
                    now = now
                )
            )
        }
    }

    /**
     * Compteurs de séries calculés depuis les tomes **effectivement visibles**.
     *
     * Le serveur expose bien un `ownedCount` par nœud, mais le recalculer ici garantit que la
     * pastille d'une série ne promet jamais plus que ce que la portée du partage laisse voir.
     */
    private fun seriesWithCounts(query: String): List<SeriesWithCounts> {
        if (!collectionType.supportsSerial) return emptyList()
        val byNode = content.records.groupBy { it.seriesId }
        val normalized = normalizeArtistName(query)
        return content.series
            .filter { series ->
                // Même filtre texte que `ObserveSeriesWithCountsUseCase` : titre ou auteur,
                // insensible à la casse et aux accents.
                normalized.isEmpty() ||
                    normalizeArtistName(series.title).contains(normalized) ||
                    normalizeArtistName(series.originalAuthor.orEmpty()).contains(normalized)
            }
            .map { series -> series.withCounts(byNode[series.id].orEmpty()) }
            .sortedBy { it.series.title.lowercase() }
    }

    private fun Series.withCounts(volumes: List<Record>): SeriesWithCounts = SeriesWithCounts(
        series = this,
        ownedVolumes = volumes.count { it.status == ItemStatus.OWNED },
        wishlistVolumes = volumes.count { it.status == ItemStatus.WISHLIST },
        ignoredVolumes = volumes.count { it.status == ItemStatus.IGNORED },
        totalVolumesInLibrary = volumes.size
    )

    /** Compteurs de l'en-tête : sur la collection entière, pas sur le résultat de la recherche. */
    private fun stats(): CollectionStats = CollectionStats(
        ownedCount = content.records.count { it.status == ItemStatus.OWNED },
        wishlistCount = content.records.count { it.status == ItemStatus.WISHLIST }
    )

    private fun Throwable.toState(): SharedCollectionContentState =
        when ((this as? SharingException)?.failure ?: SharingFailure.UNKNOWN) {
            SharingFailure.GONE -> SharedCollectionContentState.Gone
            SharingFailure.PREMIUM_REQUIRED -> SharedCollectionContentState.Suspended
            else -> SharedCollectionContentState.Failed
        }

    companion object {
        const val COLLECTION_ID_ARG: String = "collectionId"
    }
}
