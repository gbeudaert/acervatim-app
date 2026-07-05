package app.acervatim.domain.usecase

import app.acervatim.domain.repository.OAuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Codes providers connectés (état des jetons par source), médié par le backend. */
class ObserveConnectedProvidersUseCase @Inject constructor(
    private val repository: OAuthRepository
) {
    operator fun invoke(): Flow<Set<String>> = repository.connectedProviders
}

/** Recharge l'état des connexions depuis le backend (au retour au premier plan). */
class RefreshConnectedProvidersUseCase @Inject constructor(
    private val repository: OAuthRepository
) {
    suspend operator fun invoke() = repository.refresh()
}

/** Démarre la connexion d'une source ; renvoie l'`authorizeUrl` à ouvrir. */
class ConnectSourceUseCase @Inject constructor(
    private val repository: OAuthRepository
) {
    suspend operator fun invoke(providerCode: String): Result<String> =
        repository.startConnection(providerCode)
}

/** Révoque la connexion d'une source. */
class DisconnectSourceUseCase @Inject constructor(
    private val repository: OAuthRepository
) {
    suspend operator fun invoke(providerCode: String): Result<Unit> =
        repository.disconnect(providerCode)
}
