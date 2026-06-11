package fr.beudaert.acervatim

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AcervatimApplication : Application(), Configuration.Provider {

    /** Permet à WorkManager d'injecter les `@HiltWorker` (ex. [data.remote.sync.SyncWorker]). */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
