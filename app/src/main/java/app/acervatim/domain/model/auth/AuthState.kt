package app.acervatim.domain.model.auth

/** État de session applicative, observé par l'UI pour piloter connexion / sync. */
sealed interface AuthState {
    /** Aucune session : l'app reste pleinement utilisable hors-ligne ; la sync est inactive. */
    data object SignedOut : AuthState

    /** Session valide : `userId` = UUID opaque renvoyé par le back. La sync est disponible. */
    data class SignedIn(val userId: String) : AuthState

    /** Le back a renvoyé `401` : token effacé, l'UI doit proposer une reconnexion Google. */
    data object SessionExpired : AuthState
}
