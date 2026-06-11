package fr.beudaert.acervatim.data.remote.mapper

import fr.beudaert.acervatim.domain.model.AcervatimFormat
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ItemStatus
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.RecordingSpeed
import fr.beudaert.acervatim.domain.model.Series
import fr.beudaert.acervatim.domain.model.sync.RemoteItem
import fr.beudaert.acervatim.domain.model.sync.RemoteNode
import fr.beudaert.acervatim.domain.model.sync.RemoteSource
import fr.beudaert.acervatim.domain.model.sync.SyncState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Façade `Record`/`Series` (modèles ergonomiques de l'UI) ↔ modèles miroir `RemoteItem`/`RemoteNode`.
 *
 * C'est ici que vit le « rapprochement maximal du back » : encodage du `unifiedData` selon le profil
 * du type (§9), artistes ↔ `creators`, `barcode`/`isbn`/`discogsId` ↔ `sources`, mapping du statut
 * série, et **isolation des champs app-only** (sans équivalent contrat V1) dans un `localExtras`
 * conservé localement et **jamais poussé**. Aucune donnée locale n'est perdue ; seuls les champs
 * modélisés par le back transitent sur le réseau.
 *
 * Fonctions pures (aucune I/O), entièrement testables en JVM.
 */
object FacadeMapper {

    /** Holder du résultat `Record → miroir` : la part poussée ([item]) et la part locale ([localExtras]). */
    data class ItemFacade(val item: RemoteItem, val localExtras: JsonObject)

    /** Holder du résultat `Series → miroir` : la part poussée ([node]) et la part locale ([localExtras]). */
    data class NodeFacade(val node: RemoteNode, val localExtras: JsonObject)

    // ---- Record → RemoteItem ----------------------------------------------------------------

    fun recordToRemote(
        record: Record,
        collectionLocalId: String,
        nodeLocalId: String?
    ): ItemFacade {
        val unified = when (record.collectionType) {
            CollectionType.VINYL -> buildVinylUnified(record)
            CollectionType.MANGA -> buildMangaItemUnified(record)
        }
        val item = RemoteItem(
            localId = record.id,
            remoteId = null,
            collectionLocalId = collectionLocalId,
            nodeLocalId = nodeLocalId,
            volume = record.volumeNumber,
            unifiedData = unified,
            sources = record.toSources(),
            syncState = SyncState.LOCAL_ONLY,
            updatedAt = record.updatedAt
        )
        return ItemFacade(item, record.toLocalExtras())
    }

    private fun buildVinylUnified(record: Record): JsonObject = buildJsonObject {
        put("title", record.title)
        if (record.artists.isNotEmpty()) {
            put("creators", buildJsonArrayOfStrings(record.artists.map { it.name }))
        }
        record.genre?.let { put("genre", buildJsonArrayOfStrings(listOf(it))) }
        record.label?.let { put("label", it) }
        record.format?.let { put("format", it.toTypeCode()) }
        record.year?.let { put("releaseDate", "%04d-01-01".format(it)) }
        record.coverRemoteUrl?.let { put("coverUrl", it) }
    }

    private fun buildMangaItemUnified(record: Record): JsonObject = buildJsonObject {
        put("title", record.title)
        record.coverRemoteUrl?.let { put("coverUrl", it) }
    }

    /** `barcode`/`isbn`/`discogsId` → `sources[]` (providers sans/avec adapter). */
    private fun Record.toSources(): List<RemoteSource> = buildList {
        isbn?.takeIf { it.isNotBlank() }?.let { add(RemoteSource(PROVIDER_ISBN, it)) }
        barcode?.takeIf { it.isNotBlank() }?.let { add(RemoteSource(PROVIDER_BARCODE, it)) }
        discogsId?.let { add(RemoteSource(PROVIDER_DISCOGS, it.toString())) }
    }

    /** Champs app-only (sans home dans le contrat V1) — conservés localement, jamais poussés. */
    private fun Record.toLocalExtras(): JsonObject = buildJsonObject {
        rating?.let { put("rating", it) }
        purchasePrice?.let { put("purchasePrice", it) }
        recordingSpeed?.let { put("recordingSpeed", it.name) }
        country?.let { put("country", it) }
        coverPath?.let { put("coverPath", it) }
        lastPlayedAt?.let { put("lastPlayedAt", it.toString()) }
        put("status", status.name)
        // Extras manga déjà hors-contrat (publisherFr, demographic, pageCount, originalAuthor…).
        extras.forEach { (k, v) -> put("extra_$k", v) }
    }

    // ---- RemoteItem → Record (pull) ---------------------------------------------------------

    fun remoteToRecord(
        item: RemoteItem,
        type: CollectionType,
        seriesId: String?,
        localExtras: JsonObject,
        createdAt: Instant
    ): Record {
        val unified = item.unifiedData
        val sources = item.sources
        return Record(
            id = item.localId,
            collectionType = type,
            seriesId = seriesId,
            volumeNumber = item.volume,
            artists = unified.stringList("creators").map { Artist(id = UUID.randomUUID().toString(), name = it) },
            title = unified.string("title") ?: "",
            genre = unified.stringList("genre").firstOrNull(),
            year = unified.string("releaseDate")?.take(4)?.toIntOrNull(),
            label = unified.string("label"),
            format = unified.string("format")?.let { formatFromTypeCode(it) },
            recordingSpeed = localExtras.string("recordingSpeed")?.let { runCatching { RecordingSpeed.valueOf(it) }.getOrNull() },
            country = localExtras.string("country"),
            purchasePrice = localExtras["purchasePrice"]?.jsonPrimitive?.doubleOrNull,
            rating = localExtras["rating"]?.jsonPrimitive?.intOrNull,
            coverPath = localExtras.string("coverPath"),
            coverRemoteUrl = unified.string("coverUrl"),
            barcode = sources.firstOrNull { it.provider == PROVIDER_BARCODE }?.externalId,
            isbn = sources.firstOrNull { it.provider == PROVIDER_ISBN }?.externalId,
            discogsId = sources.firstOrNull { it.provider == PROVIDER_DISCOGS }?.externalId?.toLongOrNull(),
            status = localExtras.string("status")?.let { runCatching { ItemStatus.valueOf(it) }.getOrNull() } ?: ItemStatus.OWNED,
            lastPlayedAt = localExtras.string("lastPlayedAt")?.let { RemoteDtoMapper.parseInstantOrNull(it) },
            extras = localExtras.extrasMap(),
            createdAt = createdAt,
            updatedAt = item.updatedAt
        )
    }

    // ---- Series → RemoteNode ----------------------------------------------------------------

    fun seriesToRemote(series: Series, collectionLocalId: String): NodeFacade {
        val unified = buildJsonObject {
            put("title", series.title)
            series.originalAuthor?.let { put("author", it) }
            put("status", series.publicationStatus.toMangaStatusCode())
            series.totalVolumes?.let { put("totalCount", it) }
            series.synopsis?.let { put("synopsis", it) }
        }
        val node = RemoteNode(
            localId = series.id,
            remoteId = null,
            collectionLocalId = collectionLocalId,
            level = LEVEL_SERIE,
            unifiedData = unified,
            syncState = SyncState.LOCAL_ONLY,
            updatedAt = series.updatedAt
        )
        val localExtras = buildJsonObject { series.coverPath?.let { put("coverPath", it) } }
        return NodeFacade(node, localExtras)
    }

    // ---- RemoteNode → Series (pull) ---------------------------------------------------------

    fun remoteToSeries(node: RemoteNode, type: CollectionType, localExtras: JsonObject, createdAt: Instant): Series {
        val unified = node.unifiedData
        return Series(
            id = node.localId,
            collectionType = type,
            title = unified.string("title") ?: "",
            originalAuthor = unified.string("author"),
            publicationStatus = unified.string("status"),
            totalVolumes = unified["totalCount"]?.jsonPrimitive?.intOrNull,
            synopsis = unified.string("synopsis"),
            coverPath = localExtras.string("coverPath"),
            createdAt = createdAt,
            updatedAt = node.updatedAt
        )
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun AcervatimFormat.toTypeCode(): String = when (this) {
        AcervatimFormat.LP_33 -> "LP"
        AcervatimFormat.SINGLE_45 -> "Single"
        AcervatimFormat.SHELLAC_78 -> "Shellac"
        AcervatimFormat.OTHER -> "Other"
    }

    private fun formatFromTypeCode(code: String): AcervatimFormat? = when (code) {
        "LP" -> AcervatimFormat.LP_33
        "Single" -> AcervatimFormat.SINGLE_45
        "Shellac" -> AcervatimFormat.SHELLAC_78
        "Other" -> AcervatimFormat.OTHER
        else -> null
    }

    private fun String?.toMangaStatusCode(): String = when (this?.lowercase()?.trim()) {
        "en cours", "ongoing" -> "ongoing"
        "terminée", "termine", "finished", "completed" -> "finished"
        "hiatus", "pause" -> "hiatus"
        null, "" -> "unknown"
        else -> "unknown"
    }

    private fun buildJsonArrayOfStrings(values: List<String>): kotlinx.serialization.json.JsonArray =
        kotlinx.serialization.json.buildJsonArray { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    private fun JsonObject.extrasMap(): Map<String, String> = buildMap {
        this@extrasMap.forEach { (k, v) ->
            if (k.startsWith("extra_")) v.jsonPrimitive.contentOrNull?.let { put(k.removePrefix("extra_"), it) }
        }
    }

    const val PROVIDER_ISBN = "isbn"
    const val PROVIDER_BARCODE = "barcode"
    const val PROVIDER_DISCOGS = "discogs"
    const val LEVEL_SERIE = "serie"
}
