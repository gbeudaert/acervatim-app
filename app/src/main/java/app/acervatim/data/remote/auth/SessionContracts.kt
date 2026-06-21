package app.acervatim.data.remote.auth

/**
 * Fournit le JWT applicatif courant aux intercepteurs réseau, sans les coupler au stockage concret
 * ([TokenStore]). Lecture synchrone (le token est en cache mémoire derrière EncryptedSharedPreferences).
 */
interface SessionTokenProvider {
    fun currentAccessToken(): String?
}

/**
 * Notifié quand le back renvoie `401` : la session doit être invalidée et l'UI invitée à relancer
 * le login Google (§15.1, « intercepteur global »).
 */
interface UnauthorizedHandler {
    fun onUnauthorized()
}
