package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.data.backup.BackupCoverIo
import fr.beudaert.acervus.data.backup.BackupZip
import fr.beudaert.acervus.data.backup.toRecord
import fr.beudaert.acervus.data.backup.toSeries
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

class ImportBackupUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val seriesRepository: SeriesRepository,
    private val coverIo: BackupCoverIo
) {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    /**
     * Lit [input] (zip backup) et **remplace tout** : wipe records + series + covers,
     * puis ré-importe à partir du payload. Échoue avec [IllegalArgumentException]
     * si le schéma est inconnu / trop récent.
     *
     * Le caller est responsable d'avoir confirmé l'action côté UI.
     */
    suspend operator fun invoke(input: InputStream): Result = withContext(ioDispatcher) {
        val payload = BackupZip.read(input)

        require(payload.schema.version == 1) {
            "Schéma de backup non supporté : version ${payload.schema.version}"
        }

        // Wipe d'abord (séries en premier — les records ont leur seriesId basculé à NULL via FK).
        seriesRepository.deleteAll()
        recordRepository.deleteAll()
        coverIo.deleteAllCovers()

        // Restaure les séries d'abord pour que les FK records.seriesId résolvent.
        var seriesImported = 0
        payload.schema.series.forEach { dto ->
            val coverPath = dto.coverInZip
                ?.let { payload.coverBytesByZipPath[it] }
                ?.let { bytes ->
                    val target = coverIo.coverFileForSeries(dto.id)
                    target.writeBytes(bytes)
                    target.absolutePath
                }
            seriesRepository.upsert(dto.toSeries(coverPath))
            seriesImported++
        }

        var recordsImported = 0
        payload.schema.records.forEach { dto ->
            val coverPath = dto.coverInZip
                ?.let { payload.coverBytesByZipPath[it] }
                ?.let { bytes ->
                    val target = coverIo.coverFileForRecord(dto.id)
                    target.writeBytes(bytes)
                    target.absolutePath
                }
            recordRepository.upsert(dto.toRecord(coverPath))
            recordsImported++
        }

        Result(
            recordCount = recordsImported,
            seriesCount = seriesImported,
            schemaVersion = payload.schema.version
        )
    }

    data class Result(
        val recordCount: Int,
        val seriesCount: Int,
        val schemaVersion: Int
    )
}
