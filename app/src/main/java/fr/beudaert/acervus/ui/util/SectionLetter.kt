package fr.beudaert.acervus.ui.util

import java.text.Normalizer

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** First display letter for an artist name, used for alphabetical sticky headers. */
fun sectionLetterOf(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "#"
    val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD).replace(DIACRITICS, "")
    val first = normalized.firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}