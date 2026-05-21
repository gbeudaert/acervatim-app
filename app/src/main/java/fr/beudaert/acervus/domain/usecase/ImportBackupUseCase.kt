package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.repository.BackupGateway
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
    private val backupGateway: BackupGateway
) {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    /**
     * Lit [input] (zip backup) et **remplace tout** : wipe records + series + covers,
     * puis ré-importe à partir du payload. Échoue avec [IllegalArgumentException]
     * si le schéma est inconnu / trop récent ou si un identifiant est invalide
     * (validation effectuée par la gateway AVANT tout effet de bord — les données
     * existantes ne sont pas détruites en cas de payload malformé).
     *
     * Le caller est responsable d'avoir confirmé l'action côté UI.
     */
    suspend operator fun invoke(input: InputStream): Result = withContext(ioDispatcher) {
        // 1. Décodage + validation. Si l'input est malformé, throw avant tout wipe.
        val decoded = backupGateway.decode(input)

        // 2. Wipe des repositories (séries en premier — les records ont leur seriesId basculé à NULL via FK).
        seriesRepository.deleteAll()
        recordRepository.deleteAll()

        // 3. Application : la gateway gère le wipe des covers + écriture des nouvelles.
        val applied = backupGateway.apply(decoded)

        // 4. Upsert des entités restaurées.
        applied.series.forEach { seriesRepository.upsert(it) }
        applied.records.forEach { recordRepository.upsert(it) }

        Result(
            recordCount = applied.records.size,
            seriesCount = applied.series.size,
            schemaVersion = decoded.schemaVersion
        )
    }

    data class Result(
        val recordCount: Int,
        val seriesCount: Int,
        val schemaVersion: Int
    )
}
