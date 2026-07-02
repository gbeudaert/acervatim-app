package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Supprime une série, au choix de l'utilisateur (cf. `docs/interne/spec-edition-multiple.md` §2.4/§11) :
 *
 * - **Orphelins** ([cascade] = false, comportement historique) : seul le nœud série est supprimé. La FK
 *   `records.seriesId` étant en `ON DELETE SET NULL`, les tomes rattachés survivent et deviennent des
 *   items indépendants.
 * - **Cascade** ([cascade] = true) : les tomes rattachés sont supprimés **d'abord** (explicitement, sans
 *   se reposer sur la FK — chaque tome passe par [RecordRepository.deleteById] pour honorer le nettoyage
 *   des artistes orphelins et, à terme, la propagation `DELETE` en sync), puis le nœud série.
 */
class DeleteSeriesUseCase @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(seriesId: String, cascade: Boolean) {
        if (cascade) {
            recordRepository.observeBySeriesId(seriesId).first()
                .forEach { recordRepository.deleteById(it.id) }
        }
        seriesRepository.deleteById(seriesId)
    }
}
