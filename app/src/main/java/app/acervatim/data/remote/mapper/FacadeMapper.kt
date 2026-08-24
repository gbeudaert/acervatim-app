package app.acervatim.data.remote.mapper

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.readInt
import app.acervatim.domain.model.readString
import app.acervatim.domain.model.sync.RemoteItem
import app.acervatim.domain.model.sync.RemoteNode
import app.acervatim.domain.model.sync.RemoteSource
import app.acervatim.domain.model.sync.SyncState
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
 * série, statut d'item ↔ `userData.status`, et **isolation des champs app-only** (sans équivalent
 * contrat V1) dans un `localExtras` conservé localement et **jamais poussé**. Aucune donnée locale
 * n'est perdue ; seuls les champs modélisés par le back transitent sur le réseau.
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
            userData = record.toUserData(),
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
        record.recordingSpeed?.let { put(KEY_RECORDING_SPEED, it.name) }
        record.country?.let { put(KEY_COUNTRY, it) }
        record.coverRemoteUrl?.let { put("coverUrl", it) }
    }

    private fun buildMangaItemUnified(record: Record): JsonObject = buildJsonObject {
        put("title", record.title)
        record.coverRemoteUrl?.let { put("coverUrl", it) }
        record.extras.readString(MangaExtras.KEY_PUBLISHER_FR)?.let { put(MangaExtras.KEY_PUBLISHER_FR, it) }
        record.extras.readInt(MangaExtras.KEY_PAGE_COUNT)?.let { put(MangaExtras.KEY_PAGE_COUNT, it) }
        record.extras.readString(MangaExtras.KEY_SOURCE_VOLUME_RANGE)
            ?.let { put(MangaExtras.KEY_SOURCE_VOLUME_RANGE, it) }
    }

    /**
     * `userData` poussé : les champs dont **l'app est propriétaire**, nuls compris.
     *
     * Le `userData` est **mergé** côté serveur (PATCH partiel), pas remplacé. Deux conséquences :
     * - envoyer explicitement `null` est nécessaire pour qu'un effacement local (retirer une note,
     *   un prix) se propage — omettre la clé laisserait la valeur distante en place ;
     * - les clés que l'app ne modélise pas (`note`, note libre par item côté serveur) ne doivent
     *   **jamais** être écrites ici : le merge les préserve tant qu'on n'y touche pas.
     *
     * `rating` est reborné à 1..5 avant l'envoi : le schéma serveur rejette 0, et un 0 résiduel
     * (donnée héritée, import) ferait échouer le push de l'item entier en 400.
     */
    private fun Record.toUserData(): JsonObject = buildJsonObject {
        put(KEY_STATUS, status.name)
        put(KEY_RATING, rating?.takeIf { it in 1..5 })
        put(KEY_PURCHASE_PRICE, purchasePrice)
        put(KEY_LAST_PLAYED_AT, lastPlayedAt?.toString())
        put(KEY_PLAY_COUNT, playCount)
    }

    /** `barcode`/`isbn`/`discogsId` → `sources[]` (providers sans/avec adapter). */
    private fun Record.toSources(): List<RemoteSource> = buildList {
        isbn?.takeIf { it.isNotBlank() }?.let { add(RemoteSource(PROVIDER_ISBN, it)) }
        barcode?.takeIf { it.isNotBlank() }?.let { add(RemoteSource(PROVIDER_BARCODE, it)) }
        discogsId?.let { add(RemoteSource(PROVIDER_DISCOGS, it.toString())) }
    }

    /**
     * **Cliché complet du local**, réinjecté par le moteur de sync à chaque pull
     * (`preservedExtras`, reconstruit depuis le `Record` en base). Ce n'est plus un fourre-tout
     * « app-only » : depuis A1bis la quasi-totalité de ces champs est aussi poussée, et ils restent
     * écrits ici comme **filet de repli**. Sans lui, un item synchronisé avant l'ouverture des
     * canaux `userData`/`unifiedData` étendus — donc sans ces valeurs côté serveur — les perdrait
     * au premier pull. Cf. [remoteToRecord], qui lit `serveur ?: cliché`.
     *
     * Ce filet pourra tomber quand tous les items auront été re-poussés au moins une fois après
     * A1bis (le serveur détient alors la vérité) — sauf pour `coverPath` et les extras sans home
     * serveur (`edition`, `tomeSynopsis`, `demographic`, `originalAuthor`), qui restent locaux
     * par construction.
     */
    private fun Record.toLocalExtras(): JsonObject = buildJsonObject {
        rating?.let { put(KEY_RATING, it) }
        purchasePrice?.let { put(KEY_PURCHASE_PRICE, it) }
        recordingSpeed?.let { put(KEY_RECORDING_SPEED, it.name) }
        country?.let { put(KEY_COUNTRY, it) }
        // Photo prise par l'utilisateur : le backend ne stocke aucune image (invariant privacy),
        // ce champ n'a donc aucune destination serveur et reste local par construction.
        coverPath?.let { put("coverPath", it) }
        lastPlayedAt?.let { put(KEY_LAST_PLAYED_AT, it.toString()) }
        playCount.takeIf { it > 0 }?.let { put(KEY_PLAY_COUNT, it) }
        put(KEY_STATUS, status.name)
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
        val userData = item.userData
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
            recordingSpeed = (unified.string(KEY_RECORDING_SPEED) ?: localExtras.string(KEY_RECORDING_SPEED))
                ?.let { runCatching { RecordingSpeed.valueOf(it) }.getOrNull() },
            country = unified.string(KEY_COUNTRY) ?: localExtras.string(KEY_COUNTRY),
            purchasePrice = userData.double(KEY_PURCHASE_PRICE) ?: localExtras.double(KEY_PURCHASE_PRICE),
            rating = userData.int(KEY_RATING) ?: localExtras.int(KEY_RATING),
            coverPath = localExtras.string("coverPath"),
            coverRemoteUrl = unified.string("coverUrl"),
            barcode = sources.firstOrNull { it.provider == PROVIDER_BARCODE }?.externalId,
            isbn = sources.firstOrNull { it.provider == PROVIDER_ISBN }?.externalId,
            discogsId = sources.firstOrNull { it.provider == PROVIDER_DISCOGS }?.externalId?.toLongOrNull(),
            status = resolveStatus(item.userData, localExtras),
            lastPlayedAt = (userData.string(KEY_LAST_PLAYED_AT) ?: localExtras.string(KEY_LAST_PLAYED_AT))
                ?.let { RemoteDtoMapper.parseInstantOrNull(it) },
            playCount = resolvePlayCount(item.userData, localExtras),
            extras = mergedExtras(unified, localExtras),
            createdAt = createdAt,
            updatedAt = item.updatedAt
        )
    }

    /**
     * Statut effectif d'un item : `userData` (serveur) d'abord, repli sur le cliché local, défaut
     * [ItemStatus.OWNED] — même défaut que le backend (`DEFAULT_ITEM_STATUS`), donc pas de migration.
     *
     * Le repli est ce qui protège les items synchronisés avant l'ouverture du canal `userData` :
     * le serveur ne connaît pas encore leur statut, le local si. Ils se re-poussent au push suivant.
     */
    private fun resolveStatus(userData: JsonObject, localExtras: JsonObject): ItemStatus =
        userData.itemStatus() ?: localExtras.itemStatus() ?: ItemStatus.OWNED

    private fun JsonObject.itemStatus(): ItemStatus? =
        string(KEY_STATUS)?.let { runCatching { ItemStatus.valueOf(it) }.getOrNull() }

    /**
     * Compteur d'écoutes effectif : `userData` (serveur) d'abord, repli sur le cliché local,
     * défaut 0 — même défaut que le backend (`DEFAULT_PLAY_COUNT`), donc pas de migration de
     * données. Une valeur aberrante (négative) est ramenée à 0 plutôt que propagée.
     *
     * Limite assumée du modèle : le PATCH `userData` merge des **valeurs**, il n'incrémente pas.
     * Deux appareils qui écoutent hors ligne puis synchronisent s'écrasent mutuellement
     * (dernier écrivain gagne) au lieu de s'additionner. Cf. sprint SD3.
     */
    private fun resolvePlayCount(userData: JsonObject, localExtras: JsonObject): Int =
        (userData.int(KEY_PLAY_COUNT) ?: localExtras.int(KEY_PLAY_COUNT) ?: 0).coerceAtLeast(0)

    /**
     * Extras manga : ce que le serveur modélise (`publisherFr`, `pageCount`, `sourceVolumeRange`)
     * l'emporte, le cliché local comble le reste — dont les extras **sans home serveur**
     * (`edition`, `tomeSynopsis`, `demographic`, `originalAuthor`), qui ne survivent que par lui.
     */
    private fun mergedExtras(unified: JsonObject, localExtras: JsonObject): Map<String, String> {
        val fromServer = buildMap {
            unified.string(MangaExtras.KEY_PUBLISHER_FR)?.let { put(MangaExtras.KEY_PUBLISHER_FR, it) }
            unified.int(MangaExtras.KEY_PAGE_COUNT)?.let { put(MangaExtras.KEY_PAGE_COUNT, it.toString()) }
            unified.string(MangaExtras.KEY_SOURCE_VOLUME_RANGE)
                ?.let { put(MangaExtras.KEY_SOURCE_VOLUME_RANGE, it) }
        }
        return localExtras.extrasMap() + fromServer
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

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    private fun JsonObject.extrasMap(): Map<String, String> = buildMap {
        this@extrasMap.forEach { (k, v) ->
            if (k.startsWith("extra_")) v.jsonPrimitive.contentOrNull?.let { put(k.removePrefix("extra_"), it) }
        }
    }

    // Clés communes au contrat serveur et au cliché local : mêmes noms des deux côtés, ce qui rend
    // le repli `serveur ?: local` littéral (cf. [remoteToRecord]).
    const val KEY_STATUS = "status"
    const val KEY_RATING = "rating"
    const val KEY_PURCHASE_PRICE = "purchasePrice"
    const val KEY_LAST_PLAYED_AT = "lastPlayedAt"
    const val KEY_PLAY_COUNT = "playCount"
    const val KEY_RECORDING_SPEED = "recordingSpeed"
    const val KEY_COUNTRY = "country"
    const val PROVIDER_ISBN = "isbn"
    const val PROVIDER_BARCODE = "barcode"
    const val PROVIDER_DISCOGS = "discogs"
    const val LEVEL_SERIE = "serie"
}
