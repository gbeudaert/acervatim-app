package fr.beudaert.acervus.data.backup

import fr.beudaert.acervus.domain.repository.AppliedBackup
import fr.beudaert.acervus.domain.repository.BackupGateway
import fr.beudaert.acervus.domain.repository.DecodedBackup
import fr.beudaert.acervus.domain.repository.DecodedRecord
import fr.beudaert.acervus.domain.repository.DecodedSeries
import fr.beudaert.acervus.domain.repository.ExportSummary
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.Series
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation de [BackupGateway] qui encapsule le format physique (zip + JSON + covers).
 * Garde [BackupCoverIo] comme dépendance interne du module data — n'est plus exposé au
 * domaine ou à l'UI.
 */
@Singleton
class BackupGatewayImpl @Inject constructor(
    private val coverIo: BackupCoverIo
) : BackupGateway {

    override suspend fun export(
        output: OutputStream,
        records: List<Record>,
        series: List<Series>,
        exportedAt: Instant,
        appVersionName: String?
    ): ExportSummary {
        BackupZip.write(
            output = output,
            records = records,
            series = series,
            coverFileResolver = { path -> coverIo.resolveExistingCover(path) },
            exportedAt = exportedAt,
            appVersionName = appVersionName
        )
        return ExportSummary(recordCount = records.size, seriesCount = series.size)
    }

    override suspend fun decode(input: InputStream): DecodedBackup {
        val payload = BackupZip.read(input)

        require(payload.schema.version == 1) {
            "Schéma de backup non supporté : version ${payload.schema.version}"
        }

        // Valider tous les IDs AVANT toute écriture / wipe. Un payload malicieux
        // (ou simplement corrompu) ne doit pas pouvoir déclencher d'effet de bord.
        payload.schema.records.forEach { requireValidBackupId(it.id, "record") }
        payload.schema.series.forEach { requireValidBackupId(it.id, "series") }

        val decodedRecords = payload.schema.records.map { dto ->
            val bytes = dto.coverInZip?.let { payload.coverBytesByZipPath[it] }
            DecodedRecord(record = dto.toRecord(resolvedCoverPath = null), coverBytes = bytes)
        }
        val decodedSeries = payload.schema.series.map { dto ->
            val bytes = dto.coverInZip?.let { payload.coverBytesByZipPath[it] }
            DecodedSeries(series = dto.toSeries(resolvedCoverPath = null), coverBytes = bytes)
        }
        return DecodedBackup(
            schemaVersion = payload.schema.version,
            records = decodedRecords,
            series = decodedSeries
        )
    }

    override suspend fun apply(decoded: DecodedBackup): AppliedBackup {
        coverIo.deleteAllCovers()

        val series = decoded.series.map { (s, bytes) ->
            if (bytes == null) s
            else {
                val target = coverIo.coverFileForSeries(s.id)
                target.writeBytes(bytes)
                s.copy(coverPath = target.absolutePath)
            }
        }
        val records = decoded.records.map { (r, bytes) ->
            if (bytes == null) r
            else {
                val target = coverIo.coverFileForRecord(r.id)
                target.writeBytes(bytes)
                r.copy(coverPath = target.absolutePath)
            }
        }
        return AppliedBackup(records = records, series = series)
    }
}
