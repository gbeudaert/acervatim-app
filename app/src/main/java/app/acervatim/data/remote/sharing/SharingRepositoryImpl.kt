package app.acervatim.data.remote.sharing

import app.acervatim.data.local.db.CollectionDao
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CreateShareRequest
import app.acervatim.data.remote.dto.RedeemShareRequest
import app.acervatim.data.remote.dto.ShareEntryRequest
import app.acervatim.data.remote.dto.UpdateShareLabelRequest
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.mapper.SharingMapper.toDomain
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.RedeemedShare
import app.acervatim.domain.model.sharing.SharableCollection
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.model.sharing.canonical
import app.acervatim.domain.repository.SharingRepository
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partages : appels directs au backend, **sans cache**. Un partage n'a pas de vie locale — le
 * serveur est seul à savoir qui a rejoint, et le code n'existe qu'une fois, en mémoire, côté écran.
 *
 * Aucun `Log` ici : les corps de réponse contiennent le code en clair à la création, et les libellés
 * nomment souvent quelqu'un.
 */
@Singleton
class SharingRepositoryImpl @Inject constructor(
    private val api: AcervatimApi,
    private val collectionDao: CollectionDao
) : SharingRepository {

    override suspend fun sharableCollections(): List<SharableCollection> =
        collectionDao.listAll()
            .mapNotNull { entity ->
                val remoteId = entity.remoteId ?: return@mapNotNull null
                SharableCollection(
                    collectionId = remoteId,
                    name = entity.name,
                    typeCode = entity.type
                )
            }
            .sortedBy { it.name.lowercase() }

    override suspend fun shares(collectionId: String?): Result<List<CollectionShare>> =
        call { api.listShares(collectionId).map { it.toDomain() } }

    override suspend fun createShare(
        entries: Map<String, Set<ItemStatus>>,
        label: String?,
        maxUses: Int,
        expiresAt: Instant?
    ): Result<CreatedShare> = call(notFound = SharingFailure.COLLECTION_GONE) {
        api.createShare(
            CreateShareRequest(
                collections = entries.map { (collectionId, statuses) ->
                    // Ordre canonique : deux ensembles égaux doivent produire le même corps, sinon
                    // le serveur les renormalise pour rien et les diffs de test deviennent instables.
                    ShareEntryRequest(
                        collectionId = collectionId,
                        statuses = statuses.canonical().map { it.name }
                    )
                },
                label = label?.takeIf { it.isNotBlank() },
                maxUses = maxUses.coerceIn(1, MAX_USES_LIMIT),
                expiresAt = expiresAt?.toEpochMilli()
            )
        ).toDomain()
    }

    override suspend fun renameShare(shareId: String, label: String): Result<CollectionShare> =
        call { api.updateShareLabel(shareId, UpdateShareLabelRequest(label.trim())).toDomain() }

    override suspend fun revokeShare(shareId: String): Result<Unit> =
        call { api.revokeShare(shareId) }.map { }

    override suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit> =
        call { api.revokeShareMember(shareId, memberUserId) }.map { }

    override suspend fun redeem(code: String, label: String?): Result<RedeemedShare> =
        call(notFound = SharingFailure.CODE_INVALID) {
            api.redeemShare(
                RedeemShareRequest(
                    code = code.trim(),
                    label = label?.takeIf { it.isNotBlank() }
                )
            ).toDomain()
        }

    override suspend fun receivedShares(): Result<List<ReceivedShare>> =
        call { api.receivedShares().map { it.toDomain() } }

    override suspend fun renameReceivedShare(shareId: String, label: String): Result<ReceivedShare> =
        call { api.updateReceivedShareLabel(shareId, UpdateShareLabelRequest(label.trim())).toDomain() }

    /**
     * Traduit le transport en [SharingException] : les statuts HTTP s'arrêtent ici, l'UI ne raisonne
     * que sur [SharingFailure].
     *
     * [notFound] existe parce que le `404` ne veut pas dire la même chose selon la route :
     * - action sur un partage existant ⇒ « il n'est plus là » ([SharingFailure.GONE]) ;
     * - `redeem` ⇒ les cinq refus de code que le serveur confond volontairement pour ne pas offrir
     *   d'oracle ([SharingFailure.CODE_INVALID]) ;
     * - **création** ⇒ une collection envoyée est inconnue du serveur
     *   ([SharingFailure.COLLECTION_GONE]) — il n'y a pas encore de partage à déclarer disparu.
     *
     * Le distinguer ici évite à l'UI de deviner, et évite surtout un message qui ment.
     */
    private inline fun <T> call(
        notFound: SharingFailure = SharingFailure.GONE,
        block: () -> T
    ): Result<T> = runCatching(block).recoverCatching {
        throw when (it) {
            is ApiException.PaymentRequired -> SharingException(SharingFailure.PREMIUM_REQUIRED, it)
            is ApiException.NotFound -> SharingException(notFound, it)
            // Le seul refus que le serveur nomme au redeem : on rejoint son propre partage.
            is ApiException.BadRequest -> SharingException(SharingFailure.OWN_SHARE, it)
            is ApiException.TooManyRequests -> SharingException(SharingFailure.TOO_MANY_ATTEMPTS, it)
            is ApiException -> SharingException(SharingFailure.UNKNOWN, it)
            is IOException -> SharingException(SharingFailure.NETWORK, it)
            else -> SharingException(SharingFailure.UNKNOWN, it)
        }
    }

    private companion object {
        /** Même borne de validation que le backend (`CreateShareSchema.maxUses` ≤ 100). */
        const val MAX_USES_LIMIT = 100
    }
}
