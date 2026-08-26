package app.acervatim.ui.navigation

import android.net.Uri

internal object AcervatimDestinations {
    const val COLLECTION_ROUTE: String = "collection"
    const val ADD_RECORD_ROUTE: String =
        "record/add?${AcervatimNavArgs.BARCODE}={${AcervatimNavArgs.BARCODE}}" +
            "&${AcervatimNavArgs.PREFILL_KEY}={${AcervatimNavArgs.PREFILL_KEY}}"
    const val RECORD_DETAIL_ROUTE: String = "record/{${AcervatimNavArgs.RECORD_ID}}"
    const val EDIT_RECORD_ROUTE: String = "record/{${AcervatimNavArgs.RECORD_ID}}/edit"
    const val SETTINGS_ROUTE: String = "settings"
    const val MANAGE_COLLECTION_TYPES_ROUTE: String = "settings/collection-types"
    const val ACCOUNT_ROUTE: String = "account"
    /** Partages émis. Plus d'argument de collection : un partage en réunit plusieurs (S5). */
    const val SHARING_ROUTE: String = "sharing"
    const val RECEIVED_SHARES_ROUTE: String = "shares/received"
    const val SHARED_COLLECTION_ROUTE: String =
        "shared/collection/{${AcervatimNavArgs.COLLECTION_ID}}"
    const val SHARED_ITEM_ROUTE: String = "shared/item/{${AcervatimNavArgs.ITEM_ID}}"
    const val SHARED_SERIES_ROUTE: String =
        "shared/series/{${AcervatimNavArgs.COLLECTION_ID}}/{${AcervatimNavArgs.SERIES_ID}}"
    /** Recherche d'un disque par titre/artiste dans les bases externes (sans code-barres). */
    const val EXTERNAL_SEARCH_ROUTE: String =
        "search/external?${AcervatimNavArgs.COLLECTION_TYPE}={${AcervatimNavArgs.COLLECTION_TYPE}}"
    const val SCAN_ROUTE: String =
        "scan?${AcervatimNavArgs.COLLECTION_TYPE}={${AcervatimNavArgs.COLLECTION_TYPE}}" +
            "&${AcervatimNavArgs.SERIES_IMPORT}={${AcervatimNavArgs.SERIES_IMPORT}}" +
            "&${AcervatimNavArgs.SERIES_ID}={${AcervatimNavArgs.SERIES_ID}}"
    const val SERIES_IMPORT_ROUTE: String =
        "series/import?${AcervatimNavArgs.BARCODE}={${AcervatimNavArgs.BARCODE}}"
    const val SERIES_DETAIL_ROUTE: String = "series/{${AcervatimNavArgs.SERIES_ID}}"
    const val ADD_SERIES_ROUTE: String =
        "series/add?${AcervatimNavArgs.COLLECTION_TYPE}={${AcervatimNavArgs.COLLECTION_TYPE}}"
    const val EDIT_SERIES_ROUTE: String =
        "series/{${AcervatimNavArgs.SERIES_ID}}/edit"
    const val ADD_MANGA_ROUTE: String =
        "manga/add?${AcervatimNavArgs.SERIES_ID}={${AcervatimNavArgs.SERIES_ID}}" +
            "&${AcervatimNavArgs.BARCODE}={${AcervatimNavArgs.BARCODE}}" +
            "&${AcervatimNavArgs.VOLUME_NUMBER}={${AcervatimNavArgs.VOLUME_NUMBER}}"
    const val EDIT_MANGA_ROUTE: String = "manga/{${AcervatimNavArgs.RECORD_ID}}/edit"
    const val MANGA_DETAIL_ROUTE: String = "manga/{${AcervatimNavArgs.RECORD_ID}}"

    /**
     * Formulaire d'ajout d'un disque. [barcode] pré-remplit le champ code-barres (chemin du scan) ;
     * [prefillKey] désigne un match déposé au passe-plat sans code-barres associé (chemin de la
     * recherche par titre). Le scan n'a besoin que du premier : la clé y **est** le code-barres.
     */
    fun addRecord(barcode: String? = null, prefillKey: String? = null): String {
        val params = buildList {
            barcode?.takeIf { it.isNotBlank() }
                ?.let { add("${AcervatimNavArgs.BARCODE}=${Uri.encode(it)}") }
            prefillKey?.takeIf { it.isNotBlank() }
                ?.let { add("${AcervatimNavArgs.PREFILL_KEY}=${Uri.encode(it)}") }
        }
        return if (params.isEmpty()) "record/add" else "record/add?" + params.joinToString("&")
    }

    fun externalSearch(collectionType: String? = null): String {
        val encoded = collectionType?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encoded != null) "search/external?${AcervatimNavArgs.COLLECTION_TYPE}=$encoded"
        else "search/external"
    }

    fun scan(
        collectionType: String? = null,
        seriesImport: Boolean = false,
        seriesId: String? = null
    ): String {
        val params = buildList {
            collectionType?.takeIf { it.isNotBlank() }
                ?.let { add("${AcervatimNavArgs.COLLECTION_TYPE}=${Uri.encode(it)}") }
            if (seriesImport) add("${AcervatimNavArgs.SERIES_IMPORT}=true")
            seriesId?.takeIf { it.isNotBlank() }
                ?.let { add("${AcervatimNavArgs.SERIES_ID}=${Uri.encode(it)}") }
        }
        return if (params.isEmpty()) "scan" else "scan?" + params.joinToString("&")
    }

    fun seriesImport(barcode: String): String =
        "series/import?${AcervatimNavArgs.BARCODE}=${Uri.encode(barcode)}"

    /** Consultation d'une collection reçue en partage (lecture seule, en ligne). */
    fun sharedCollection(collectionId: String): String =
        "shared/collection/${Uri.encode(collectionId)}"

    fun sharedItem(itemId: String): String = "shared/item/${Uri.encode(itemId)}"

    /** Le détail d'une série partagée porte l'id de collection : il en filtre les tomes. */
    fun sharedSeries(collectionId: String, seriesId: String): String =
        "shared/series/${Uri.encode(collectionId)}/${Uri.encode(seriesId)}"

    fun recordDetail(id: String): String = "record/$id"
    fun editRecord(id: String): String = "record/$id/edit"
    fun seriesDetail(id: String): String = "series/$id"

    fun addSeries(collectionType: String? = null): String {
        val encoded = collectionType?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encoded != null) "series/add?${AcervatimNavArgs.COLLECTION_TYPE}=$encoded"
        else "series/add"
    }

    fun editSeries(id: String): String = "series/$id/edit"

    fun addManga(
        seriesId: String? = null,
        barcode: String? = null,
        volumeNumber: Int? = null
    ): String {
        val params = buildList<String> {
            seriesId?.takeIf { it.isNotBlank() }?.let { add("${AcervatimNavArgs.SERIES_ID}=${Uri.encode(it)}") }
            barcode?.takeIf { it.isNotBlank() }?.let { add("${AcervatimNavArgs.BARCODE}=${Uri.encode(it)}") }
            volumeNumber?.let { add("${AcervatimNavArgs.VOLUME_NUMBER}=$it") }
        }
        return if (params.isEmpty()) "manga/add" else "manga/add?" + params.joinToString("&")
    }

    fun editManga(id: String): String = "manga/$id/edit"

    fun mangaDetail(id: String): String = "manga/$id"
}

internal object AcervatimNavArgs {
    const val RECORD_ID: String = "recordId"
    const val BARCODE: String = "barcode"
    const val SERIES_ID: String = "seriesId"
    const val COLLECTION_TYPE: String = "collectionType"
    const val VOLUME_NUMBER: String = "volumeNumber"
    /** Clé d'un match déposé dans `ScanPrefillHandoff` quand elle n'est pas un code-barres. */
    const val PREFILL_KEY: String = "prefillKey"
    const val SERIES_IMPORT: String = "seriesImport"
    const val COLLECTION_ID: String = "collectionId"
    const val ITEM_ID: String = "itemId"
}
