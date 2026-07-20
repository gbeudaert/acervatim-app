package app.acervatim.domain.usecase

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Édition d'un champ dans un delta d'édition groupée (§2.2, tri-état) :
 * - [Keep] : ne pas modifier — la valeur d'origine (potentiellement **différente d'une entrée à
 *   l'autre**) est conservée.
 * - [Set] : définir la même valeur sur toutes les entrées.
 * - [Clear] : effacer (mettre à `null`/absent) — réservé aux champs nullables.
 */
sealed interface FieldEdit<out T> {
    data object Keep : FieldEdit<Nothing>
    data object Clear : FieldEdit<Nothing>
    data class Set<T>(val value: T) : FieldEdit<T>
}

/**
 * Delta d'édition groupée (§3). Seuls les champs en [FieldEdit.Set]/[FieldEdit.Clear] sont modifiés ;
 * les champs [FieldEdit.Keep] (défaut) préservent la valeur existante de chaque entrée.
 *
 * **Le titre n'y figure pas** (édition unitaire uniquement, §3), pas plus que les identifiants uniques
 * (`barcode`/`isbn`/`discogsId`) ni l'identité structurelle d'un tome (`seriesId`/`volumeNumber`).
 */
data class BulkEditDelta(
    val status: FieldEdit<ItemStatus> = FieldEdit.Keep,
    val rating: FieldEdit<Int> = FieldEdit.Keep,
    val purchasePrice: FieldEdit<Double> = FieldEdit.Keep,
    val genre: FieldEdit<String> = FieldEdit.Keep,
    val year: FieldEdit<Int> = FieldEdit.Keep,
    val label: FieldEdit<String> = FieldEdit.Keep,
    val format: FieldEdit<AcervatimFormat> = FieldEdit.Keep,
    val recordingSpeed: FieldEdit<RecordingSpeed> = FieldEdit.Keep,
    val country: FieldEdit<String> = FieldEdit.Keep,
    /** Remplacement **complet** de la liste d'artistes (pas de merge, §3). */
    val artists: FieldEdit<List<String>> = FieldEdit.Keep,
    // Extras manga (capability-driven, §3) — stockés dans `Record.extras`.
    val publisherFr: FieldEdit<String> = FieldEdit.Keep,
    val originalAuthor: FieldEdit<String> = FieldEdit.Keep,
    val demographic: FieldEdit<String> = FieldEdit.Keep,
    val pageCount: FieldEdit<String> = FieldEdit.Keep
) {
    /** Aucun champ n'est en Set/Clear : appliquer le delta n'aurait aucun effet. */
    val isEmpty: Boolean
        get() = listOf(
            status, rating, purchasePrice, genre, year, label, format, recordingSpeed, country,
            artists, publisherFr, originalAuthor, demographic, pageCount
        ).all { it is FieldEdit.Keep }
}

/** Échec d'édition d'une entrée (validation ou unicité) — n'interrompt pas le lot (§6.1). */
data class BulkEditFailure(val id: String, val reason: String?)

/** Bilan d'une édition groupée. */
data class BulkEditResult(
    val updated: List<String> = emptyList(),
    val failed: List<BulkEditFailure> = emptyList()
) {
    val updatedCount: Int get() = updated.size
    val failedCount: Int get() = failed.size
    val hasFailures: Boolean get() = failed.isNotEmpty()
}

/**
 * Applique un [BulkEditDelta] à une sélection d'entrées (§6.1). Pour chaque id : projette les valeurs
 * courantes de l'entrée dans un [RecordInput], écrase **uniquement** les champs en Set/Clear, puis
 * délègue à [UpdateRecordUseCase] (validation + contrôle d'unicité + upsert). Une entrée en échec
 * (validation/unicité) est collectée dans [BulkEditResult.failed] sans interrompre le lot.
 */
class UpdateRecordsUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val updateRecord: UpdateRecordUseCase
) {
    suspend operator fun invoke(ids: Collection<String>, delta: BulkEditDelta): BulkEditResult {
        if (delta.isEmpty) return BulkEditResult()

        val updated = mutableListOf<String>()
        val failed = mutableListOf<BulkEditFailure>()

        for (id in ids.distinct()) {
            val record = recordRepository.observeById(id).first() ?: continue
            try {
                updateRecord(record, delta.applyTo(record))
                updated += id
            } catch (e: DuplicateRecordException) {
                failed += BulkEditFailure(id, e.message)
            } catch (e: IllegalArgumentException) {
                failed += BulkEditFailure(id, e.message)
            }
        }

        return BulkEditResult(updated = updated, failed = failed)
    }
}

/** Projette [record] en [RecordInput] puis y applique les champs en Set/Clear du delta. */
internal fun BulkEditDelta.applyTo(record: Record): RecordInput {
    val extras = record.extras.toMutableMap()
    extras.apply(MangaExtras.KEY_PUBLISHER_FR, publisherFr)
    extras.apply(MangaExtras.KEY_ORIGINAL_AUTHOR, originalAuthor)
    extras.apply(MangaExtras.KEY_DEMOGRAPHIC, demographic)
    extras.apply(MangaExtras.KEY_PAGE_COUNT, pageCount)

    return RecordInput(
        artists = when (val e = artists) {
            is FieldEdit.Set -> e.value
            else -> record.artists.map { it.name }
        },
        title = record.title,
        collectionType = record.collectionType,
        seriesId = record.seriesId,
        volumeNumber = record.volumeNumber,
        genre = genre.resolve(record.genre),
        year = year.resolve(record.year),
        label = label.resolve(record.label),
        format = format.resolve(record.format),
        recordingSpeed = recordingSpeed.resolve(record.recordingSpeed),
        country = country.resolve(record.country),
        purchasePrice = purchasePrice.resolve(record.purchasePrice),
        rating = rating.resolve(record.rating),
        coverPath = record.coverPath,
        coverRemoteUrl = record.coverRemoteUrl,
        barcode = record.barcode,
        isbn = record.isbn,
        discogsId = record.discogsId,
        // `status` n'est pas nullable : un éventuel Clear retombe sur la valeur courante (non exposé en UI).
        status = status.resolve(record.status) ?: record.status,
        extras = extras
    )
}

/** Keep → valeur courante ; Clear → null ; Set → nouvelle valeur. */
private fun <T> FieldEdit<T>.resolve(current: T?): T? = when (this) {
    is FieldEdit.Keep -> current
    is FieldEdit.Clear -> null
    is FieldEdit.Set -> value
}

/** Applique un [FieldEdit] textuel à une clé d'extras (Set = écrire, Clear = retirer, Keep = laisser). */
private fun MutableMap<String, String>.apply(key: String, edit: FieldEdit<String>) {
    when (edit) {
        is FieldEdit.Keep -> Unit
        is FieldEdit.Clear -> remove(key)
        is FieldEdit.Set -> put(key, edit.value)
    }
}
