package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.CreatedShareDto
import app.acervatim.data.remote.dto.ReceivedEntryDto
import app.acervatim.data.remote.dto.ReceivedShareDto
import app.acervatim.data.remote.dto.RedeemedShareDto
import app.acervatim.data.remote.dto.ShareDto
import app.acervatim.data.remote.dto.ShareEntryDto
import app.acervatim.data.remote.dto.ShareMemberDto
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ReceivedEntry
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.RedeemedShare
import app.acervatim.domain.model.sharing.ShareEntry
import app.acervatim.domain.model.sharing.ShareMember
import java.time.Instant

/** Mapping DTO partage (`/v1/shares/…`) → modèles domain. */
object SharingMapper {

    /**
     * Un statut inconnu est **écarté**, pas replié sur une valeur par défaut : un backend en avance
     * sur l'app exposerait sinon plus que ce que l'écran annonce. Ensemble vide possible en sortie —
     * l'UI le rend comme « rien d'exposé », ce qui est le bon sens de la défaillance pour un partage.
     */
    private fun List<String>.toStatuses(): Set<ItemStatus> =
        mapNotNull { code -> ItemStatus.entries.firstOrNull { it.name == code.uppercase() } }.toSet()

    fun ShareEntryDto.toDomain(): ShareEntry = ShareEntry(
        collectionId = collectionId,
        name = name,
        typeCode = type,
        statuses = statuses.toStatuses()
    )

    fun ReceivedEntryDto.toDomain(): ReceivedEntry = ReceivedEntry(
        collectionId = collectionId,
        name = name,
        typeCode = type,
        statuses = statuses.toStatuses(),
        itemCount = itemCount
    )

    fun ShareMemberDto.toDomain(): ShareMember = ShareMember(
        memberUserId = memberUserId,
        redeemedAt = RemoteDtoMapper.parseInstant(redeemedAt)
    )

    fun ShareDto.toDomain(): CollectionShare = CollectionShare(
        id = id,
        label = label,
        maxUses = maxUses,
        usedCount = usedCount,
        expiresAt = expiresAt?.let(Instant::ofEpochMilli),
        createdAt = RemoteDtoMapper.parseInstant(createdAt),
        collections = collections.map { it.toDomain() },
        members = members.map { it.toDomain() }
    )

    fun CreatedShareDto.toDomain(): CreatedShare = CreatedShare(
        share = CollectionShare(
            id = id,
            label = label,
            maxUses = maxUses,
            usedCount = usedCount,
            expiresAt = expiresAt?.let(Instant::ofEpochMilli),
            createdAt = RemoteDtoMapper.parseInstant(createdAt),
            collections = collections.map { it.toDomain() },
            members = members.map { it.toDomain() }
        ),
        code = code
    )

    fun ReceivedShareDto.toDomain(): ReceivedShare = ReceivedShare(
        shareId = shareId,
        label = label,
        redeemedAt = RemoteDtoMapper.parseInstant(redeemedAt),
        collections = collections.map { it.toDomain() }
    )

    fun RedeemedShareDto.toDomain(): RedeemedShare = RedeemedShare(
        share = ReceivedShare(
            shareId = shareId,
            label = label,
            redeemedAt = RemoteDtoMapper.parseInstant(redeemedAt),
            collections = collections.map { it.toDomain() }
        ),
        alreadyMember = alreadyMember
    )
}
