package app.acervatim.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import app.acervatim.domain.repository.SyncRepository
import java.util.concurrent.TimeUnit

/**
 * Synchronisation planifiée en arrière-plan (couche au-dessus du déclenchement manuel). Contrainte
 * réseau ; échec ⇒ `retry` (backoff WorkManager). Le moteur lui-même ([SyncManagerImpl]) ignore
 * l'appel si aucune session n'est active.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        syncRepository.syncNow().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )

    companion object {
        private const val UNIQUE_NAME = "acervatim-periodic-sync"

        /** Planifie une synchro périodique (~6 h), réseau requis. Idempotent (KEEP). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
