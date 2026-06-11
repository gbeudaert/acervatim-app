package fr.beudaert.acervatim.domain.model.sync

/**
 * État de synchronisation d'une ressource locale vis-à-vis du backend (résolution last-write-wins).
 */
enum class SyncState {
    /** Créée localement, jamais poussée — n'a pas encore de `remoteId`. */
    LOCAL_ONLY,

    /** Alignée avec le serveur (`remoteId` présent, pas de modification locale en attente). */
    SYNCED,

    /** Modifiée localement depuis le dernier `SYNCED` — à pousser (`PATCH`). */
    DIRTY,

    /** Supprimée localement — à propager (`DELETE`) puis purger. */
    PENDING_DELETE
}
