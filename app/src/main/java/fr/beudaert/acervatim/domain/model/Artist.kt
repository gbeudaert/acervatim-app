package fr.beudaert.acervatim.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id: String,
    val name: String
)

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normalizeArtistName(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    val nfd = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
    return nfd.replace(DIACRITICS, "").lowercase()
}
