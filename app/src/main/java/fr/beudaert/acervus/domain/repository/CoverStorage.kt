package fr.beudaert.acervus.domain.repository

/**
 * Persiste les images de couverture sur le stockage local. L'interface est volontairement
 * domain-pure (URIs passées sous forme de `String`) ; la parsing `android.net.Uri` est gérée
 * côté implémentation pour ne pas faire fuiter Android dans le domaine.
 */
interface CoverStorage {
    /** Persiste l'image référencée par `uriString` (content:// ou file://). Renvoie le chemin absolu local. */
    suspend fun saveFromUri(recordId: String, uriString: String): String

    /** Persiste l'image fournie en octets bruts. Renvoie le chemin absolu local. */
    suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String

    /** Télécharge l'image depuis une URL HTTP(S) et la persiste. Renvoie le chemin absolu local. */
    suspend fun saveFromUrl(recordId: String, url: String): String

    /** Supprime la cover associée à `recordId`, si elle existe. */
    suspend fun delete(recordId: String)

    /** Renvoie le chemin absolu attendu pour la cover de `recordId` (sans vérifier l'existence). */
    fun pathFor(recordId: String): String
}
