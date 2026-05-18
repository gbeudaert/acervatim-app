package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.RecordArtistEntry
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class ObserveCollectionEntriesUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val settingsRepository: SettingsRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(filter: RecordFilter): Flow<List<RecordArtistEntry>> =
        settingsRepository.observeDuplicateAlbumsPerArtist()
            .distinctUntilChanged()
            .flatMapLatest { duplicate ->
                if (duplicate) {
                    recordRepository.observeFlattenedByArtist(filter)
                } else {
                    recordRepository.observeByPrimaryArtist(filter)
                }
            }
}
