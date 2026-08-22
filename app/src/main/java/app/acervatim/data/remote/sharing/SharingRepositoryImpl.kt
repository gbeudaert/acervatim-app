package app.acervatim.data.remote.sharing

import app.acervatim.data.local.db.CollectionDao
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CreateShareRequest
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.mapper.SharingMapper.toDomain
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ShareScope
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharingRepository
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partages : appels directs au backend, **sans cache**. Un partage n'a pas de vie locale — le
 * serveur est seul à savoir qui a rejoint, et le code n'existe qu'une fois, en mémoire, côté écran.
 *
 * Aucun `Log` ici : les corps de réponse contiennent le code en clair à la création.
 */
@Singleton
class SharingRepositoryImpl @Inject constructor(
    private val api: AcervatimApi,
    private val collectionDao: CollectionDao
) : SharingRepository {

    override suspend fun remoteCollectionId(type: CollectionType): String? =
        collectionDao.getFirstByType(type.typeCode)?.remoteId

    override suspend fun shares(collectionId: String): Result<List<CollectionShare>> =
        call { api.listShares(collectionId).map { it.toDomain() } }

    override suspend fun createShare(
        collectionId: String,
        scope: ShareScope,
        maxUses: Int,
        expiresAt: Instant?
    ): Result<CreatedShare> = call {
        api.createShare(
            id = collectionId,
            body = CreateShareRequest(
                scope = scope.code,
                maxUses = maxUses,
                expiresAt = expiresAt?.toEpochMilli()
            )
        ).toDomain()
    }

    override suspend fun revokeShare(shareId: String): Result<Unit> =
        call { api.revokeShare(shareId) }.map { }

    override suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit> =
        call { api.revokeShareMember(shareId, memberUserId) }.map { }

    /**
     * Traduit le transport en [SharingException] : les statuts HTTP s'arrêtent ici, l'UI ne raisonne
     * que sur [SharingFailure].
     */
    private inline fun <T> call(block: () -> T): Result<T> = runCatching(block).recoverCatching {
        throw when (it) {
            is ApiException.PaymentRequired -> SharingException(SharingFailure.PREMIUM_REQUIRED, it)
            is ApiException -> SharingException(SharingFailure.UNKNOWN, it)
            is IOException -> SharingException(SharingFailure.NETWORK, it)
            else -> SharingException(SharingFailure.UNKNOWN, it)
        }
    }
}
