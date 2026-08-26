package app.acervatim.domain.model

/**
 * Cause d'échec d'une recherche externe, traduite depuis le transport dans la couche `data` — l'UI
 * n'a pas à connaître les statuts HTTP (même contrat que `SharingFailure`).
 *
 * Chaque valeur existe parce qu'elle appelle une **réponse différente** à l'écran : sans cette
 * distinction, un refus de jeton s'afficherait « aucun résultat » et l'utilisateur en conclurait
 * que son disque n'est pas référencé.
 */
enum class ExternalSearchFailure {

    /**
     * `403 source-token-required` : la source exige un jeton d'accès (compte utilisateur rattaché,
     * ou repli sur l'accès partagé premium). Rien à retenter — il faut une action de l'utilisateur.
     */
    SOURCE_TOKEN_REQUIRED,

    /** Réseau injoignable, délai dépassé, ou source externe indisponible (502/503). Retentable. */
    NETWORK,

    /** `429` : trop de requêtes. Retentable après une pause. */
    RATE_LIMITED,

    /** Tout le reste (4xx/5xx inattendu) : rien d'actionnable à dire de plus. */
    UNKNOWN
}

/**
 * Échec d'une recherche externe. [provider] nomme la source concernée quand le serveur la précise
 * (extension `provider` du problem+json `source-token-required`) : c'est elle qui décide vers quel
 * parcours router l'utilisateur.
 */
class ExternalSearchException(
    val failure: ExternalSearchFailure,
    val provider: String? = null,
    cause: Throwable? = null
) : Exception(cause?.message, cause)
