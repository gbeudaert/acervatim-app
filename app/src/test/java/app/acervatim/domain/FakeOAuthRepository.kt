package app.acervatim.domain

import app.acervatim.domain.repository.OAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeOAuthRepository(
    initialConnected: Set<String> = emptySet(),
    private val authorizeUrl: String = "https://auth.example/authorize",
    private var connectFails: Boolean = false
) : OAuthRepository {

    private val connected = MutableStateFlow(initialConnected)
    var refreshCount = 0
        private set

    override val connectedProviders: Flow<Set<String>> = connected.asStateFlow()

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun startConnection(providerCode: String): Result<String> =
        if (connectFails) Result.failure(IllegalStateException("boom")) else Result.success(authorizeUrl)

    override suspend fun disconnect(providerCode: String): Result<Unit> {
        connected.value = connected.value - providerCode
        return Result.success(Unit)
    }

    /** Helper de test : simule un provider fraîchement connecté (retour du navigateur). */
    fun markConnected(providerCode: String) {
        connected.value = connected.value + providerCode
    }
}
