package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Source d'un item ou d'un nœud (§6/§7). En lecture normale, `rawData` n'est jamais inclus :
 * il faut passer par `GET …/sources` ([SourceWithRawDto]).
 */
@Serializable
data class SourceDto(
    val provider: String,
    val externalId: String,
    /** ISO-8601 ou `null` (pas encore enrichie par un adapter). */
    val fetchedAt: String? = null
)

/** Variante avec snapshot brut, renvoyée par `GET /v1/items/:id/sources` et `…/nodes/:id/sources`. */
@Serializable
data class SourceWithRawDto(
    val provider: String,
    val externalId: String,
    val rawData: JsonElement? = null,
    val fetchedAt: String? = null
)

/** Corps de `POST …/sources` (attacher une source). */
@Serializable
data class AttachSourceRequest(
    val provider: String,
    val externalId: String
)

/** Référence `{ provider, externalId }` — utilisée pour `node`/`source` à la création. */
@Serializable
data class SourceRefDto(
    val provider: String,
    val externalId: String
)
