package app.acervatim.data.remote.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste la date de la dernière synchronisation réussie, pour la réafficher après un
 * redémarrage de l'app. Sans ça, le [app.acervatim.domain.model.sync.SyncStatus] ne vit qu'en
 * mémoire dans [SyncManagerImpl] et repart à `Idle` à chaque lancement du process.
 *
 * Prefs en clair (pas de chiffrement) : un horodatage n'est pas un secret, contrairement au JWT
 * stocké par [app.acervatim.data.remote.auth.TokenStore]. Effacé en même temps que la session.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Instant de la dernière sync réussie (`Success`/`LimitReached`), `null` si jamais synchronisé. */
    var lastSyncAt: Instant?
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_SYNC_AT) else putLong(KEY_LAST_SYNC_AT, value.toEpochMilli())
            }.apply()
        }

    /** Oublie la dernière sync — appelé à la fin de session pour ne pas afficher la date d'un autre compte. */
    fun clear() {
        prefs.edit().remove(KEY_LAST_SYNC_AT).apply()
    }

    private companion object {
        const val PREFS_NAME = "acervatim_sync"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}