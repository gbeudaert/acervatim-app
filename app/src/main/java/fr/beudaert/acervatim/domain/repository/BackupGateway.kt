package fr.beudaert.acervatim.domain.repository

import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.Series
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Passerelle domain-pure pour la lecture / écriture de backups. Cache l'implémentation
 * physique (format zip + JSON + covers) au domaine. Les use cases dépendent uniquement
 * de cette interface — aucun import `fr.beudaert.acervatim.data.*` n'est requis côté domaine.
 */
interface BackupGateway {
    /** Écrit un backup contenant `records`, `series` et leurs covers locales dans `output`. */
    suspend fun export(
        output: OutputStream,
        records: List<Record>,
        series: List<Series>,
        exportedAt: Instant,
        appVersionName: String?
    ): ExportSummary

    /**
     * Lit + valide le payload. **Aucun effet de bord filesystem** — la fonction throw
     * `IllegalArgumentException` si le schéma est incompatible, les IDs invalides, ou
     * tout autre format non supporté. Sûr à appeler avant tout wipe des repositories.
     */
    suspend fun decode(input: InputStream): DecodedBackup

    /**
     * Applique un payload précédemment décodé : vide le dossier covers, écrit les
     * nouvelles covers sur disque, renvoie les records / series avec `coverPath` résolu.
     * À appeler **après** le wipe des repositories.
     */
    suspend fun apply(decoded: DecodedBackup): AppliedBackup
}

data class ExportSummary(val recordCount: Int, val seriesCount: Int)

data class DecodedBackup(
    val schemaVersion: Int,
    val records: List<DecodedRecord>,
    val series: List<DecodedSeries>
)

/** Record décodé + bytes de cover à matérialiser sur disque (null = pas de cover). */
data class DecodedRecord(val record: Record, val coverBytes: ByteArray?)

/** Idem pour une série. */
data class DecodedSeries(val series: Series, val coverBytes: ByteArray?)

data class AppliedBackup(val records: List<Record>, val series: List<Series>)
