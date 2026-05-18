package fr.beudaert.acervus.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes (DataStore).
 */
interface SettingsRepository {

    /**
     * Lorsque `true`, un album multi-artistes apparaît une fois par artiste dans la liste
     * collection (sous chaque lettre du sticky header alphabétique). Lorsque `false`,
     * il n'apparaît que sous son artiste principal.
     */
    fun observeDuplicateAlbumsPerArtist(): Flow<Boolean>

    suspend fun setDuplicateAlbumsPerArtist(value: Boolean)
}
