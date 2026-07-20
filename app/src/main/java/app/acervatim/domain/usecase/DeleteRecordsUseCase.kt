package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Suppression groupée d'une sélection (§2.4, lot L5). Gère une sélection mixte d'**entrées/tomes** et
 * de **séries** ; en pratique les deux ne coexistent pas dans une même vue (§9), mais le use case les
 * accepte ensemble par robustesse.
 *
 * - **entrée/tome** → [DeleteRecordUseCase].
 * - **série** → [DeleteSeriesUseCase] avec le mode choisi par l'utilisateur (§2.4/§11) : **cascade**
 *   (série + tomes) ou **orphelins** ([cascade] = false, nœud seul, tomes conservés via
 *   `ON DELETE SET NULL`).
 *
 * Le use case **ne confirme rien** : l'UI affiche l'avertissement et choisit le mode **avant** de
 * l'appeler (§2.4).
 */
class DeleteRecordsUseCase @Inject constructor(
    private val deleteRecord: DeleteRecordUseCase,
    private val deleteSeries: DeleteSeriesUseCase,
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(
        recordIds: Collection<String> = emptyList(),
        seriesIds: Collection<String> = emptyList(),
        cascade: Boolean = false
    ): DeleteResult {
        var deletedRecords = 0
        var deletedSeries = 0
        var deletedTomes = 0

        for (id in recordIds.distinct()) {
            deleteRecord(id)
            deletedRecords++
        }

        for (id in seriesIds.distinct()) {
            // Compter les tomes cascadés avant la suppression (snapshot).
            if (cascade) {
                deletedTomes += recordRepository.observeBySeriesId(id).first().size
            }
            deleteSeries(id, cascade)
            deletedSeries++
        }

        return DeleteResult(
            deletedRecords = deletedRecords,
            deletedSeries = deletedSeries,
            deletedTomes = deletedTomes
        )
    }
}

/** Bilan d'une suppression groupée. [deletedTomes] = tomes supprimés par cascade de série. */
data class DeleteResult(
    val deletedRecords: Int = 0,
    val deletedSeries: Int = 0,
    val deletedTomes: Int = 0
) {
    /** Nombre total d'éléments supprimés (entrées + séries + tomes cascadés). */
    val total: Int get() = deletedRecords + deletedSeries + deletedTomes
}
