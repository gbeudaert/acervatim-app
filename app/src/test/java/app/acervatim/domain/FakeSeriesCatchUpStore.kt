package app.acervatim.domain

import app.acervatim.domain.model.SeriesCatchUpState
import app.acervatim.domain.repository.SeriesCatchUpStore

/** [SeriesCatchUpStore] en mémoire ; [writes] compte les écritures (pas d'écriture inutile). */
class FakeSeriesCatchUpStore : SeriesCatchUpStore {

    val states: MutableMap<String, SeriesCatchUpState> = mutableMapOf()
    var writes: Int = 0
    var currentOwner: String? = null
    val cleared: MutableList<String> = mutableListOf()
    var clearAllCalls: Int = 0

    /** Échec programmable de [write], pour tester les filets d'erreur des appelants. */
    var failOnWrite: Throwable? = null

    override suspend fun read(seriesId: String): SeriesCatchUpState =
        states[seriesId] ?: SeriesCatchUpState()

    override suspend fun write(seriesId: String, state: SeriesCatchUpState) {
        failOnWrite?.let { throw it }
        writes++
        states[seriesId] = state
    }

    override suspend fun clear(seriesId: String) {
        cleared += seriesId
        states.remove(seriesId)
    }

    override suspend fun owner(): String? = currentOwner

    override suspend fun clearAll(owner: String?) {
        clearAllCalls++
        states.clear()
        currentOwner = owner
    }
}
