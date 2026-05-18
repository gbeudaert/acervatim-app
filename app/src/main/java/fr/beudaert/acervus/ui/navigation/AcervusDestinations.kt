package fr.beudaert.acervus.ui.navigation

import android.net.Uri

internal object AcervusDestinations {
    const val COLLECTION_ROUTE: String = "collection"
    const val ADD_RECORD_ROUTE: String =
        "record/add?${AcervusNavArgs.BARCODE}={${AcervusNavArgs.BARCODE}}"
    const val RECORD_DETAIL_ROUTE: String = "record/{${AcervusNavArgs.RECORD_ID}}"
    const val EDIT_RECORD_ROUTE: String = "record/{${AcervusNavArgs.RECORD_ID}}/edit"
    const val SETTINGS_ROUTE: String = "settings"
    const val SCAN_ROUTE: String = "scan"

    fun addRecord(barcode: String? = null): String {
        val encoded = barcode?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encoded != null) "record/add?${AcervusNavArgs.BARCODE}=$encoded" else "record/add"
    }

    fun recordDetail(id: String): String = "record/$id"
    fun editRecord(id: String): String = "record/$id/edit"
}

internal object AcervusNavArgs {
    const val RECORD_ID: String = "recordId"
    const val BARCODE: String = "barcode"
}
