package app.acervatim.ui.scan

import app.acervatim.domain.model.ExternalMatch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passe-plat en mémoire pour transporter le résultat enrichi du scan ([ExternalMatch]) du
 * [BarcodeScanViewModel] vers le formulaire d'ajout, sans le sérialiser dans la route de navigation
 * (la jaquette/description seraient trop volumineuses et fragiles en arg de nav). La navigation dépile
 * `SCAN_ROUTE`, donc on ne peut pas s'appuyer sur son `SavedStateHandle` : ce singleton survit.
 *
 * Le match est associé à son code-barres : le formulaire ne le consomme que s'il correspond au code
 * reçu en argument, et le vide à la lecture (usage unique).
 */
@Singleton
class ScanPrefillHandoff @Inject constructor() {

    private var pendingBarcode: String? = null
    private var pendingMatch: ExternalMatch? = null

    /** Mémorise le match retenu pour `barcode`. Écrase tout match en attente. */
    fun put(barcode: String, match: ExternalMatch) {
        pendingBarcode = barcode
        pendingMatch = match
    }

    /** Récupère et efface le match si (et seulement si) il correspond à `barcode`. */
    fun take(barcode: String): ExternalMatch? {
        if (barcode.isBlank() || barcode != pendingBarcode) return null
        val match = pendingMatch
        pendingBarcode = null
        pendingMatch = null
        return match
    }
}
