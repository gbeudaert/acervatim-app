package app.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Modèle `Collection` (§5). Le client manipule `type` (code stable) ; l'UUID interne `typeId`
 * du back n'est jamais exposé.
 */
@Serializable
data class CollectionDto(
    val id: String,
    val userId: String,
    val type: String,
    val name: String,
    val description: String? = null,
    val itemCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    /** Présent uniquement sur `GET /v1/collections/:id` ; `[]` pour les types plats. */
    val hierarchy: List<HierarchyLevelDto>? = null
)

@Serializable
data class HierarchyLevelDto(
    val key: String,
    val label: String,
    val count: Int
)

/** Corps de `POST /v1/collections`. */
@Serializable
data class CreateCollectionRequest(
    val typeCode: String,
    val name: String,
    val description: String? = null
)

/** Corps de `PATCH /v1/collections/:id` (au moins un champ). */
@Serializable
data class PatchCollectionRequest(
    val name: String? = null,
    val description: String? = null
)
