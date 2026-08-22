package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.CreatedShareDto
import app.acervatim.data.remote.dto.ShareDto
import app.acervatim.data.remote.dto.ShareMemberDto
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ShareMember
import app.acervatim.domain.model.sharing.ShareScope
import java.time.Instant

/** Mapping DTO partage (`/v1/collections/:id/shares`, `/v1/shares/…`) → modèles domain. */
object SharingMapper {

    fun ShareDto.toDomain(): CollectionShare = CollectionShare(
        id = id,
        collectionId = collectionId,
        scope = ShareScope.fromCode(scope),
        maxUses = maxUses,
        usedCount = usedCount,
        expiresAt = expiresAt?.let(Instant::ofEpochMilli),
        createdAt = RemoteDtoMapper.parseInstant(createdAt),
        members = members.map { it.toDomain() }
    )

    fun ShareMemberDto.toDomain(): ShareMember = ShareMember(
        memberUserId = memberUserId,
        redeemedAt = RemoteDtoMapper.parseInstant(redeemedAt)
    )

    fun CreatedShareDto.toDomain(): CreatedShare = CreatedShare(
        id = id,
        code = code,
        scope = ShareScope.fromCode(scope),
        maxUses = maxUses,
        expiresAt = expiresAt?.let(Instant::ofEpochMilli)
    )
}
