package app.acervatim.data.local.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection
import java.time.Instant

/**
 * Construction de la requête « collection filtrée et triée ».
 *
 * Écrite à la main plutôt qu'en `@Query` parce que l'`ORDER BY` dépend du tri choisi : en
 * annotation il aurait fallu un `CASE WHEN` par champ **et** par sens, soit douze branches à
 * maintenir dans une chaîne illisible. Ici la clause est composée depuis une énumération — donc
 * jamais depuis une saisie utilisateur, il n'y a rien à injecter — et toutes les valeurs passent
 * par des paramètres liés.
 */
internal object RecordQueries {

    /**
     * Le tri porte sur des **disques**, pas sur les lignes jointes disque × artiste : ordonner
     * les lignes ne trie pas les disques (un album à trois artistes produit trois lignes qui se
     * mélangeraient aux autres). D'où un `ORDER BY` dont les clés sont toutes des propriétés du
     * disque, `ra.position` ne servant qu'à ranger les artistes *à l'intérieur* d'un disque.
     */
    fun filteredJoinRows(
        filter: RecordFilter,
        sort: RecordSort,
        notPlayedSince: Instant?
    ): SupportSQLiteQuery {
        val query = filter.query.trim()
        val collectionType = filter.collectionType?.name
        val status = filter.status.toItemStatus()?.name
        val format = filter.format?.name
        val notPlayedSinceMillis = notPlayedSince?.toEpochMilli()

        val sql = """
            SELECT r.*,
                   a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
            FROM records r
            INNER JOIN record_artists ra ON ra.recordId = r.id
            INNER JOIN artists a ON a.id = ra.artistId
            WHERE (
                    ? = ''
                    OR r.title LIKE '%' || ? || '%'
                    OR EXISTS (
                        SELECT 1 FROM record_artists ra2
                        INNER JOIN artists a2 ON a2.id = ra2.artistId
                        WHERE ra2.recordId = r.id AND a2.name LIKE '%' || ? || '%'
                    )
                )
              AND (? IS NULL OR r.collectionType = ?)
              AND (? IS NULL OR r.status = ?)
              AND (? IS NULL OR r.genre = ?)
              AND (? IS NULL OR r.format = ?)
              AND (? IS NULL OR r.lastPlayedAt IS NULL OR r.lastPlayedAt < ?)
            ORDER BY ${orderBy(sort)}
        """.trimIndent()

        val args = arrayOf<Any?>(
            query, query, query,
            collectionType, collectionType,
            status, status,
            filter.genre, filter.genre,
            format, format,
            notPlayedSinceMillis, notPlayedSinceMillis
        )
        return SimpleSQLiteQuery(sql, args)
    }

    /**
     * Clause d'ordre complète : absents en fin de liste, clé demandée, puis départage.
     *
     * `NULLS LAST` n'est pas utilisable — il demande SQLite 3.30, alors que l'app descend à
     * l'API 24 où le SQLite embarqué est bien plus ancien. Le `CASE WHEN … IS NULL` fait le même
     * travail partout, et **hors** de l'inversion de sens : sans lui, passer en décroissant
     * remonterait tous les disques jamais écoutés en tête d'un tri par dernière écoute.
     *
     * Le départage `r.title`, `r.id` rend l'ordre **total** : deux disques de même année ne
     * peuvent plus permuter d'un affichage à l'autre. `ra.position` ferme la marche pour que les
     * artistes d'un même disque sortent dans leur ordre déclaré.
     */
    private fun orderBy(sort: RecordSort): String {
        val key = sortKey(sort.field)
        val direction = if (sort.direction == SortDirection.ASCENDING) "ASC" else "DESC"
        return "CASE WHEN $key IS NULL THEN 1 ELSE 0 END ASC, " +
                "$key $direction, " +
                "r.title COLLATE NOCASE ASC, r.id ASC, ra.position ASC"
    }

    /**
     * Limite connue et partagée avec le comparateur : `NOCASE` ne replie que l'ASCII, et aucun
     * des deux moteurs n'applique de collation locale. « Ágætis » se range donc après « Zoo »,
     * pas près de « A ». Corriger cela demanderait une collation dédiée des deux côtés à la
     * fois — sans quoi les deux ordres se mettraient à diverger, ce qui serait pire.
     */
    private fun sortKey(field: RecordSortField): String = when (field) {
        // Artiste principal = celui de position la plus basse. Sous-requête corrélée plutôt que
        // `a.name` : sur une ligne jointe, `a` est *un* artiste du disque, pas forcément le
        // principal — trier dessus mélangerait les disques à plusieurs artistes.
        RecordSortField.ARTIST -> PRIMARY_ARTIST_NAME
        RecordSortField.TITLE -> "r.title COLLATE NOCASE"
        RecordSortField.YEAR -> "r.year"
        RecordSortField.LAST_PLAYED -> "r.lastPlayedAt"
        RecordSortField.ADDED -> "r.createdAt"
        RecordSortField.RATING -> "r.rating"
    }

    /**
     * `COLLATE NOCASE` est **hors** de la sous-requête : à l'intérieur il porterait sur le
     * `SELECT` interne, où il ne change rien, et l'`ORDER BY` retomberait sur la comparaison
     * binaire — qui range toutes les majuscules avant toutes les minuscules (« Zed » avant
     * « mel »). Le comparateur Kotlin, lui, compare en minuscules.
     */
    private const val PRIMARY_ARTIST_NAME =
        "(SELECT a2.name FROM record_artists ra2 " +
                "INNER JOIN artists a2 ON a2.id = ra2.artistId " +
                "WHERE ra2.recordId = r.id ORDER BY ra2.position LIMIT 1) COLLATE NOCASE"
}
