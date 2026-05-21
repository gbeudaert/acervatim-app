package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.repository.BackupGateway
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val seriesRepository: SeriesRepository,
    private val backupGateway: BackupGateway,
    private val clock: Clock
) {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    /**
     * Écrit le backup dans [output] (zip JSON + covers). Le caller est responsable de
     * fermer le stream — typiquement obtenu d'un `ContentResolver.openOutputStream(uri)`.
     *
     * Renvoie le nombre d'items embarqués (utile pour les snackbars de succès).
     */
    suspend operator fun invoke(
        output: OutputStream,
        appVersionName: String? = null
    ): Result = withContext(ioDispatcher) {
        val records = recordRepository.listAll()
        val series = seriesRepository.listAll()
        val summary = backupGateway.export(
            output = output,
            records = records,
            series = series,
            exportedAt = clock.instant(),
            appVersionName = appVersionName
        )
        Result(recordCount = summary.recordCount, seriesCount = summary.seriesCount)
    }

    data class Result(val recordCount: Int, val seriesCount: Int)
}
