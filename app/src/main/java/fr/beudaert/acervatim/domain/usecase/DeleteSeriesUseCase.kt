package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.repository.SeriesRepository
import javax.inject.Inject

/**
 * Supprime une série. La FK `records.seriesId` est en `ON DELETE SET NULL`, donc les tomes
 * déjà rattachés ne sont pas effacés : ils deviennent des items "orphelins" (cohérent avec
 * une suppression de série côté utilisateur qui ne doit pas faire disparaître ses tomes).
 */
class DeleteSeriesUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(seriesId: String) {
        repository.deleteById(seriesId)
    }
}
