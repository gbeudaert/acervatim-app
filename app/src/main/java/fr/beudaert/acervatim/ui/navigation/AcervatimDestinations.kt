package fr.beudaert.acervatim.ui.navigation

import android.net.Uri

internal object AcervatimDestinations {
    const val COLLECTION_ROUTE: String = "collection"
    const val ADD_RECORD_ROUTE: String =
        "record/add?${AcervatimNavArgs.BARCODE}={${AcervatimNavArgs.BARCODE}}"
    const val RECORD_DETAIL_ROUTE: String = "record/{${AcervatimNavArgs.RECORD_ID}}"
    const val EDIT_RECORD_ROUTE: String = "record/{${AcervatimNavArgs.RECORD_ID}}/edit"
    const val SETTINGS_ROUTE: String = "settings"
    const val ACCOUNT_ROUTE: String = "account"
    const val SCAN_ROUTE: String = "scan"
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

    fun addRecord(barcode: String? = null): String {
        val encoded = barcode?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encoded != null) "record/add?${AcervatimNavArgs.BARCODE}=$encoded" else "record/add"
    }

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
}

internal object AcervatimNavArgs {
    const val RECORD_ID: String = "recordId"
    const val BARCODE: String = "barcode"
    const val SERIES_ID: String = "seriesId"
    const val COLLECTION_TYPE: String = "collectionType"
    const val VOLUME_NUMBER: String = "volumeNumber"
}
