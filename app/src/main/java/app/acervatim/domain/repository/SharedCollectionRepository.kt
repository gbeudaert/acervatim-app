package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.sharing.SharedItemDetail

/**
 * Tout le contenu visible d'une collection reçue en partage, tel qu'il a été chargé.
 *
 * [series] est vide pour un type plat. Les tomes sont dans [records] dans les deux cas : c'est
 * l'écran qui les regroupe par série, exactement comme il le fait pour les collections locales.
 */
data class SharedCollectionContent(
    val records: List<Record>,
    val series: List<Series> = emptyList()
)

/**
 * Lecture **en ligne** d'une collection reçue en partage.
 *
 * Aucune route dédiée : le partage n'ouvre pas de second chemin de lecture, il étend l'autorisation
 * des routes `collections`/`items`/`nodes` existantes (sprint backend S4). Le filtrage par statuts
 * est appliqué **serveur-side** — l'app n'a aucun filtre de portée à poser et ne pourrait pas en
 * demander d'autres.
 *
 * **Rien n'est mis en cache.** Ces données ne sont pas celles de l'utilisateur : elles n'entrent ni
 * dans Room, ni dans l'export, ni dans la sauvegarde ZIP, et peuvent disparaître à tout moment
 * (révocation, éjection, propriétaire qui perd son premium).
 *
 * D'où le **chargement d'un bloc** plutôt qu'une pagination à l'écran : sans table où filtrer, la
 * recherche et le tri se font en mémoire, et ils ne peuvent porter que sur ce qui est déjà chargé.
 * Une pagination paresseuse rendrait une recherche silencieusement incomplète — un résultat faux
 * plutôt qu'un résultat lent.
 */
interface SharedCollectionRepository {

    /**
     * Charge la collection entière : ses éléments, et ses séries si le type est hiérarchique.
     *
     * [type] vient de l'entrée de partage (`GET /v1/shares/received`) : le serveur y donne le code
     * de type de chaque collection exposée, l'app n'a pas à le deviner.
     */
    suspend fun load(collectionId: String, type: CollectionType): Result<SharedCollectionContent>

    /**
     * Détail d'un élément partagé, avec le `userData` réduit que S4 laisse passer vers un membre.
     *
     * Appel séparé et non extrait de [load] : la vue liste est une projection légère, elle ne porte
     * ni les sources, ni les champs de détail.
     */
    suspend fun item(itemId: String): Result<SharedItemDetail>

    /**
     * Une série partagée et **ses seuls tomes**.
     *
     * Chargement ciblé plutôt que réutilisation du [load] complet : le serveur sait filtrer les
     * éléments par nœud (`GET /v1/collections/:id/items?nodeId=`), et rouvrir une série ne doit pas
     * coûter le rechargement de toute la collection. Rien n'est mis en cache — comme partout ici.
     *
     * `null` si le nœud n'existe pas ou n'est plus visible : le partage a changé sous les pieds.
     */
    suspend fun series(
        collectionId: String,
        seriesId: String,
        type: CollectionType
    ): Result<SharedSeries?>
}

/** Une série partagée, avec les tomes que la portée du partage laisse voir. */
data class SharedSeries(
    val series: Series,
    val volumes: List<Record>
)
