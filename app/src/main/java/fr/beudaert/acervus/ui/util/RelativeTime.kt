package fr.beudaert.acervus.ui.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/**
 * Formats [ts] relative to [now] following the spec rules (§6 — Dernière écoute).
 * Returns "À l'instant", "Aujourd'hui à HH:mm", "Il y a N jours", or a long date.
 */
fun formatRelativeTime(
    ts: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val delta = Duration.between(ts, now)
    return when {
        delta.isNegative -> FULL_DATE.format(LocalDateTime.ofInstant(ts, zone))
        delta.toHours() < 1 -> "À l'instant"
        delta.toHours() < 24 -> "Aujourd'hui à ${SHORT_TIME.format(LocalDateTime.ofInstant(ts, zone))}"
        delta.toDays() < 7 -> "Il y a ${delta.toDays()} jour${if (delta.toDays() > 1) "s" else ""}"
        else -> FULL_DATE.format(LocalDateTime.ofInstant(ts, zone))
    }
}