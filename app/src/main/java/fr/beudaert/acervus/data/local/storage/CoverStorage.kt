package fr.beudaert.acervus.data.local.storage

import android.net.Uri

interface CoverStorage {
    suspend fun saveFromUri(recordId: String, uri: Uri): String
    suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String
    suspend fun saveFromUrl(recordId: String, url: String): String
    suspend fun delete(recordId: String)
    fun pathFor(recordId: String): String
}
