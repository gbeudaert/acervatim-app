package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Enregistre une écoute : horodate (`lastPlayedAt`) **et** incrémente le compteur
 * (`playCount`). Le compteur alimente la rubrique « les plus écoutés » de la galerie ;
 * il est poussé au serveur en valeur absolue par la synchro.
 */
class MarkAsPlayedUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(id: String) {
        repository.markPlayed(id, clock.instant())
    }
}
