package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.SeriesCatchUpState
import app.acervatim.domain.repository.SeriesCatchUpStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** DataStore dédié au rattrapage, distinct des préférences utilisateur (`acervatim_settings`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeriesCatchUpDataStore

/**
 * [SeriesCatchUpStore] sur un DataStore Preferences : une entrée JSON par série.
 *
 * Pourquoi pas Room : la migration v8 vient d'être faite, et cet état n'a rien d'une donnée de
 * collection — ni synchronisé, ni sauvegardé, recalculable par une vérification. Une colonne de
 * `series` l'aurait fait voyager dans la synchro et les sauvegardes pour rien.
 */
@Singleton
class DataStoreSeriesCatchUpStore @Inject constructor(
    @SeriesCatchUpDataStore private val dataStore: DataStore<Preferences>
) : SeriesCatchUpStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(seriesId: String): SeriesCatchUpState {
        val raw = dataStore.data.first()[keyOf(seriesId)] ?: return SeriesCatchUpState()
        // Une entrée illisible (format d'une autre version) n'est qu'un cache perdu : on repart à vide.
        return runCatching { json.decodeFromString<StoredCatchUp>(raw).toDomain() }
            .getOrDefault(SeriesCatchUpState())
    }

    override suspend fun write(seriesId: String, state: SeriesCatchUpState) {
        val encoded = json.encodeToString(StoredCatchUp.of(state))
        dataStore.edit { it[keyOf(seriesId)] = encoded }
    }

    override suspend fun clear(seriesId: String) {
        dataStore.edit { it.remove(keyOf(seriesId)) }
    }

    override suspend fun owner(): String? = dataStore.data.first()[OWNER_KEY]

    override suspend fun clearAll(owner: String?) {
        // DataStore dédié au rattrapage : tout ce qu'il contient est à oublier, propriétaire compris.
        dataStore.edit { prefs ->
            prefs.clear()
            owner?.let { prefs[OWNER_KEY] = it }
        }
    }

    private fun keyOf(seriesId: String) = stringPreferencesKey("catch_up:$seriesId")

    private companion object {
        /** Utilisateur pour lequel l'état a été calculé (cf. [SeriesCatchUpStore.owner]). */
        val OWNER_KEY = stringPreferencesKey("catch_up_owner")
    }
}

@Serializable
private data class StoredCatchUp(
    val lastCheckedOn: String? = null,
    val dismissedOn: String? = null,
    val pending: List<StoredTome> = emptyList()
) {
    fun toDomain() = SeriesCatchUpState(
        lastCheckedOn = lastCheckedOn?.let(::parseDate),
        dismissedOn = dismissedOn?.let(::parseDate),
        pending = pending.map { it.toDomain() }
    )

    companion object {
        fun of(state: SeriesCatchUpState) = StoredCatchUp(
            lastCheckedOn = state.lastCheckedOn?.toString(),
            dismissedOn = state.dismissedOn?.toString(),
            pending = state.pending.map(StoredTome::of)
        )
    }
}

@Serializable
private data class StoredTome(
    val editionVolume: Int,
    val isbn: String? = null,
    val titleFr: String? = null,
    val sourceVolumeRange: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val noticeCreatedAt: String? = null
) {
    fun toDomain() = EditionTome(
        editionVolume = editionVolume,
        isbn = isbn,
        titleFr = titleFr,
        sourceVolumeRange = sourceVolumeRange,
        coverUrl = coverUrl,
        description = description,
        noticeCreatedAt = noticeCreatedAt?.let(::parseDate)
    )

    companion object {
        fun of(tome: EditionTome) = StoredTome(
            editionVolume = tome.editionVolume,
            isbn = tome.isbn,
            titleFr = tome.titleFr,
            sourceVolumeRange = tome.sourceVolumeRange,
            coverUrl = tome.coverUrl,
            description = tome.description,
            noticeCreatedAt = tome.noticeCreatedAt?.toString()
        )
    }
}

private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
