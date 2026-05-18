package fr.beudaert.acervus.domain.model

/**
 * Vitesse de gravure (RPM) — distincte du format physique.
 * Exemple : un 33 t peut être gravé à 45 tr/min pour une meilleure dynamique.
 */
enum class RecordingSpeed {
    RPM_33,
    RPM_45,
    RPM_78,
    OTHER
}
