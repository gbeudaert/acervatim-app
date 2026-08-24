package app.acervatim.data.remote.sharing

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.NodeDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.RECORD_BY_VOLUME
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.sharing.SharedFact
import app.acervatim.domain.model.sharing.SharedFactKey
import app.acervatim.domain.model.sharing.SharedItemDetail
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionContent
import app.acervatim.domain.repository.SharedCollectionRepository
import app.acervatim.domain.repository.SharedSeries
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecture en ligne d'une collection partagée. **Aucune écriture, aucun cache.**
 *
 * La vue liste arrive en `JsonObject` (projection légère dépendant du type, §9.3) : on n'en extrait
 * que les clés communes aux profils, en tolérant l'absence. Une clé inconnue est ignorée plutôt que
 * de faire échouer la page — un backend en avance sur l'app ne doit pas casser une vue en lecture.
 */
@Singleton
class SharedCollectionRepositoryImpl @Inject constructor(
    private val api: AcervatimApi
) : SharedCollectionRepository {

    override suspend fun load(
        collectionId: String,
        type: CollectionType
    ): Result<SharedCollectionContent> = call {
        val nodes = if (type.supportsSerial) fetchNodes(collectionId) else emptyList()
        // `title` est nullable sur un noeud : un noeud sans titre n'a rien a preter a ses tomes.
        val serieTitles = nodes.mapNotNull { node -> node.title?.let { node.id to it } }.toMap()
        SharedCollectionContent(
            records = fetchItems(collectionId).mapNotNull { it.toRecord(type, serieTitles) },
            series = nodes.map { it.toSeries(type) }
        )
    }

    /**
     * Suit la pagination cursor jusqu'au bout.
     *
     * [MAX_PAGES] n'est pas une limite produit mais un garde-fou : un `nextCursor` qui ne bougerait
     * pas — bug serveur, proxy fantaisiste — boucherait l'app indéfiniment sur un écran de
     * chargement. Mieux vaut une collection tronquée qu'une application figée.
     */
    private suspend fun fetchItems(
        collectionId: String,
        nodeId: String? = null
    ): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = api.listItems(
                collectionId,
                cursor = cursor,
                limit = PAGE_SIZE,
                nodeId = nodeId
            )
            all += page.data
            cursor = page.meta.pagination.nextCursor
            pages++
        } while (cursor != null && pages < MAX_PAGES)
        return all
    }

    private suspend fun fetchNodes(collectionId: String): List<NodeDto> {
        val all = mutableListOf<NodeDto>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = api.listNodes(collectionId, cursor = cursor, limit = PAGE_SIZE)
            all += page.data
            cursor = page.meta.pagination.nextCursor
            pages++
        } while (cursor != null && pages < MAX_PAGES)
        return all
    }

    override suspend fun item(itemId: String): Result<SharedItemDetail> =
        call { api.getItem(itemId).toSharedDetail() }

    override suspend fun series(
        collectionId: String,
        seriesId: String,
        type: CollectionType
    ): Result<SharedSeries?> = call {
        val node = api.getNode(seriesId)
        val titles = node.title?.let { mapOf(node.id to it) }.orEmpty()
        SharedSeries(
            series = node.toSeries(type),
            volumes = fetchItems(collectionId, nodeId = seriesId)
                .mapNotNull { it.toRecord(type, titles) }
                .sortedWith(RECORD_BY_VOLUME)
        )
    }

    // ---- Projection légère -> Record ------------------------------------------------------------

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            .orEmpty()

    /**
     * Statut projeté par le serveur. Une valeur inconnue retombe sur [ItemStatus.OWNED], comme
     * `resolveItemStatus` côté serveur et comme le défaut de l'app : un item sans statut est possédé.
     */
    private fun JsonObject.status(): ItemStatus =
        string("status")
            ?.let { code -> ItemStatus.entries.firstOrNull { it.name == code.uppercase() } }
            ?: ItemStatus.OWNED

    /**
     * Sans `id`, la ligne n'est ni ouvrable ni identifiable en clé de liste : on l'écarte.
     *
     * `format` et `recordingSpeed` restent `null` : la projection légère ne les porte pas. La puce de
     * fin de ligne est donc absente sur une collection partagée — les obtenir demanderait un appel de
     * détail par élément, ce qui n'est pas un prix raisonnable pour une puce.
     */
    private fun JsonObject.toRecord(
        type: CollectionType,
        serieTitles: Map<String, String>
    ): Record? {
        val id = string("id") ?: return null
        val nodeId = string("nodeId")
        // Titre de la série : le serveur le projette (`serie`), mais un nœud chargé à part fait
        // autorité — c'est lui qui pilote l'affichage en mode série.
        val serie = nodeId?.let(serieTitles::get) ?: string("serie")
        val creators = stringList("creators")
        // Un manga n'a pas de « creators » projetés : l'auteur affiché est celui de la série, sinon
        // la ligne n'aurait aucune section où se ranger en vue plate.
        val names = creators.ifEmpty { listOfNotNull(serie) }
        return Record(
            id = id,
            collectionType = type,
            seriesId = nodeId,
            volumeNumber = int("volume"),
            artists = names.map { Artist(id = artistId(it), name = it) },
            title = string("title").orEmpty(),
            genre = stringList("genre").firstOrNull(),
            coverRemoteUrl = string("coverUrl"),
            status = status(),
            createdAt = EPOCH,
            updatedAt = EPOCH
        )
    }

    /**
     * Identifiant d'artiste **synthétique**, dérivé du nom.
     *
     * Le serveur ne renvoie pas d'entité artiste dans la projection légère, et il n'y en a pas à
     * inventer : cet id ne sert qu'à distinguer deux lignes du même disque dans la clé de liste. Le
     * dériver du nom le rend stable d'un chargement à l'autre, ce qu'un UUID aléatoire ne serait pas.
     */
    private fun artistId(name: String): String = "shared-artist:${name.lowercase()}"

    private fun ItemDto.toSharedDetail(): SharedItemDetail {
        val u = unifiedData
        val facts = buildList {
            u.stringList("creators").takeIf { it.isNotEmpty() }
                ?.let { add(SharedFact(SharedFactKey.CREATORS, it.joinToString(", "))) }
            u.stringList("genre").takeIf { it.isNotEmpty() }
                ?.let { add(SharedFact(SharedFactKey.GENRE, it.joinToString(", "))) }
            u.string("label")?.let { add(SharedFact(SharedFactKey.LABEL, it)) }
            u.string("format")?.let { add(SharedFact(SharedFactKey.FORMAT, it)) }
            u.string("releaseDate")?.let { add(SharedFact(SharedFactKey.RELEASE_DATE, it)) }
            volume?.let { add(SharedFact(SharedFactKey.VOLUME, it.toString())) }
        }
        return SharedItemDetail(
            id = id,
            title = u.string("title"),
            coverUrl = u.string("coverUrl"),
            volume = volume,
            status = userData.string("status")
                ?.let { code -> ItemStatus.entries.firstOrNull { it.name == code.uppercase() } },
            // Absents du JSON pour un membre (S4 réduit le `userData`) ⇒ `null`, et l'écran n'affiche
            // alors rien. Ne pas confondre avec « présent mais vide ».
            rating = userData.int("rating"),
            lastPlayedAt = userData.string("lastPlayedAt"),
            facts = facts
        )
    }

    private fun NodeDto.toSeries(type: CollectionType): Series = Series(
        id = id,
        collectionType = type,
        title = title.orEmpty(),
        originalAuthor = author,
        publicationStatus = status,
        totalVolumes = totalCount,
        synopsis = synopsis,
        coverRemoteUrl = coverUrl,
        createdAt = EPOCH,
        updatedAt = EPOCH
    )

    /**
     * Traduit le transport en [SharingException], comme `SharingRepositoryImpl`.
     *
     * Deux cas portent tout le cycle de vie d'un partage côté membre :
     * - `404` ⇒ [SharingFailure.GONE] : partage révoqué ou membre éjecté, l'accès est coupé net.
     * - `402` ⇒ [SharingFailure.PREMIUM_REQUIRED] : le **propriétaire** a perdu son premium. C'est une
     *   **suspension**, pas une suppression — l'accès revient s'il repaie. L'écran doit le dire ainsi,
     *   et surtout ne rien purger (il n'y a de toute façon rien à purger).
     */
    private inline fun <T> call(block: () -> T): Result<T> = runCatching(block).recoverCatching {
        throw when (it) {
            is ApiException.PaymentRequired -> SharingException(SharingFailure.PREMIUM_REQUIRED, it)
            is ApiException.NotFound -> SharingException(SharingFailure.GONE, it)
            is ApiException -> SharingException(SharingFailure.UNKNOWN, it)
            is IOException -> SharingException(SharingFailure.NETWORK, it)
            else -> SharingException(SharingFailure.UNKNOWN, it)
        }
    }

    private companion object {
        const val PAGE_SIZE = 100

        /** Garde-fou anti-boucle, pas un plafond produit : 100 × 100 = 10 000 éléments. */
        const val MAX_PAGES = 100

        /**
         * `createdAt`/`updatedAt` ne sont pas projetés en vue liste et ne servent à rien ici : la vue
         * partagée ne trie ni n'affiche par date. Une constante explicite vaut mieux qu'un
         * `Instant.now()` qui changerait à chaque chargement et casserait l'égalité des `Record`.
         */
        val EPOCH: Instant = Instant.EPOCH
    }
}
