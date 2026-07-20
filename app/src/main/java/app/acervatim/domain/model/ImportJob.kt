package app.acervatim.domain.model

/**
 * Job d'import de série **asynchrone** (fire-and-poll) côté backend : énumération BnF + résolution des
 * ~30 jaquettes, sorti du cycle requête-réponse du scan pour supprimer le timeout client (incident
 * 0.5.3). L'app lance le job, poll son [state]/[progress], puis — au `DONE` — re-tape `edition-mapping`
 * (servi du cache serveur réchauffé par le worker). Cf. `edition-import.types.ts` (backend).
 */
data class ImportJob(
    /** Identifiant déterministe (hash titre+édition) : dédup inter-user, opaque côté client. */
    val jobId: String,
    val state: ImportJobState,
    /** Avancement courant, ou `null` tant que le worker n'a rien mesuré (job en file). */
    val progress: ImportJobProgress? = null
)

/** État simplifié exposé à l'app (projection des états BullMQ). */
enum class ImportJobState {
    QUEUED,
    RUNNING,
    DONE,
    FAILED;

    companion object {
        /** Projette la valeur brute du DTO ; toute valeur inconnue est traitée comme [QUEUED] (prudent). */
        fun fromWire(raw: String?): ImportJobState = when (raw) {
            "running" -> RUNNING
            "done" -> DONE
            "failed" -> FAILED
            else -> QUEUED // queued + inconnu
        }
    }
}

/** Avancement d'un job d'import (publié par le worker via `job.updateProgress`). */
data class ImportJobProgress(
    val phase: ImportPhase,
    /** Éléments traités dans la phase courante. */
    val done: Int,
    /** Total d'éléments de la phase courante (0 si pas encore connu). */
    val total: Int
)

/** Phase de traitement du worker d'import. */
enum class ImportPhase {
    /** Énumération des tomes de l'édition auprès de la BnF. */
    ENUMERATING,

    /** Résolution des jaquettes (Google Books) tome par tome. */
    COVERS;

    companion object {
        fun fromWire(raw: String?): ImportPhase = when (raw) {
            "covers" -> COVERS
            else -> ENUMERATING // enumerating + inconnu
        }
    }
}
