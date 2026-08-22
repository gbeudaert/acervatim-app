package app.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Partage de collection (`/v1/collections/:id/shares`, `/v1/shares/...`).
 *
 * Les portées voyagent en minuscules (`all` / `owned` / `wantlist`) : le backend stocke et accepte
 * la même valeur, il n'y a pas de mapping API↔base à faire diverger. Côté app, la conversion vers
 * [app.acervatim.domain.model.sharing.ShareScope] est faite par le mapper.
 */

/** Corps de `POST /v1/collections/:id/shares`. `expiresAt` en epoch ms ; absent = jamais. */
@Serializable
data class CreateShareRequest(
    val scope: String,
    val maxUses: Int? = null,
    val expiresAt: Long? = null
)

/**
 * Réponse de `POST /v1/collections/:id/shares` — **seule** réponse qui porte [code] en clair. Le
 * serveur n'en stocke que le HMAC : ni [listShares][app.acervatim.data.remote.api.AcervatimApi.listShares]
 * ni aucune autre route ne le rendra. À ne jamais persister côté app (cf. `SharingViewModel`).
 */
@Serializable
data class CreatedShareDto(
    val id: String,
    val code: String,
    val collectionId: String,
    val scope: String,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val expiresAt: Long? = null,
    val createdAt: String
)

/** Élément de `GET /v1/collections/:id/shares` (liste nue, pas d'enveloppe [PageDto]). */
@Serializable
data class ShareDto(
    val id: String,
    val collectionId: String,
    val scope: String,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val expiresAt: Long? = null,
    val createdAt: String,
    val members: List<ShareMemberDto> = emptyList()
)

/**
 * Membre ayant rejoint un partage. [memberUserId] est un UUID **opaque** : les comptes sont
 * anonymes côté serveur (aucun email, aucun nom), il n'y a rien d'autre à afficher.
 */
@Serializable
data class ShareMemberDto(
    val memberUserId: String,
    val redeemedAt: String
)
