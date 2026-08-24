package app.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Partages de collection (`/v1/shares/...`), contrat du sprint backend S5.
 *
 * Un partage porte **N collections**, chacune avec son jeu de statuts : il n'appartient plus à une
 * collection, d'où l'ancrage des routes sur `/v1/shares` et la disparition du champ `scope`.
 *
 * Les statuts voyagent en majuscules (`OWNED` / `WISHLIST` / `IGNORED`), même vocabulaire que
 * `userData.status` d'un item : rien à traduire entre l'API, la base et le filtrage.
 */

/** Une collection à exposer, dans le corps de `POST /v1/shares`. `statuses` non vide. */
@Serializable
data class ShareEntryRequest(
    val collectionId: String,
    val statuses: List<String>
)

/**
 * Corps de `POST /v1/shares`. `expiresAt` en epoch ms ; absent = le code ne périme jamais.
 *
 * `label` est l'aide-mémoire du propriétaire : optionnel, jamais transmis aux membres.
 */
@Serializable
data class CreateShareRequest(
    val collections: List<ShareEntryRequest>,
    val label: String? = null,
    val maxUses: Int? = null,
    val expiresAt: Long? = null
)

/**
 * Corps de `PATCH /v1/shares/:id` et `PATCH /v1/shares/received/:shareId`.
 *
 * [label] est **non nullable ici** alors que le serveur accepte `null` : le `Json` de l'app est
 * configuré en `explicitNulls = false`, un `null` ne serait donc pas sérialisé et le schéma strict
 * (`label` requis) refuserait le corps vide. On envoie la chaîne vide, que le serveur normalise
 * lui-même en `null` — même effet, sans se battre contre la config de sérialisation.
 */
@Serializable
data class UpdateShareLabelRequest(
    val label: String
)

/** Corps de `POST /v1/shares/redeem`. `label` = le nom que le **membre** donne au partage. */
@Serializable
data class RedeemShareRequest(
    val code: String,
    val label: String? = null
)

/** Une collection exposée par un partage, telle que la voit le propriétaire. */
@Serializable
data class ShareEntryDto(
    val collectionId: String,
    val name: String,
    val type: String,
    val statuses: List<String> = emptyList()
)

/**
 * Idem côté membre, avec le nombre d'éléments visibles sous ces statuts. Le propriétaire n'a pas ce
 * compte : le serveur ne le lui calcule pas.
 */
@Serializable
data class ReceivedEntryDto(
    val collectionId: String,
    val name: String,
    val type: String,
    val statuses: List<String> = emptyList(),
    val itemCount: Int = 0
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

/** Élément de `GET /v1/shares` (liste nue, pas d'enveloppe [PageDto]). */
@Serializable
data class ShareDto(
    val id: String,
    val label: String? = null,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val expiresAt: Long? = null,
    val createdAt: String,
    val collections: List<ShareEntryDto> = emptyList(),
    val members: List<ShareMemberDto> = emptyList()
)

/**
 * Réponse de `POST /v1/shares` — **seule** réponse qui porte [code] en clair. Le serveur n'en stocke
 * que le HMAC : ni `GET /v1/shares` ni aucune autre route ne le rendra. À ne jamais persister ni
 * journaliser côté app (cf. `SharingViewModel`).
 */
@Serializable
data class CreatedShareDto(
    val id: String,
    val code: String,
    val label: String? = null,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val expiresAt: Long? = null,
    val createdAt: String,
    val collections: List<ShareEntryDto> = emptyList(),
    val members: List<ShareMemberDto> = emptyList()
)

/** Élément de `GET /v1/shares/received` : un partage que j'ai rejoint, avec **mon** libellé. */
@Serializable
data class ReceivedShareDto(
    val shareId: String,
    val label: String? = null,
    val redeemedAt: String,
    val collections: List<ReceivedEntryDto> = emptyList()
)

/** Réponse de `POST /v1/shares/redeem` : idem, plus l'idempotence. */
@Serializable
data class RedeemedShareDto(
    val shareId: String,
    val label: String? = null,
    val redeemedAt: String,
    val collections: List<ReceivedEntryDto> = emptyList(),
    /** `true` si j'étais déjà membre : aucune place n'a été consommée. */
    val alreadyMember: Boolean = false
)
