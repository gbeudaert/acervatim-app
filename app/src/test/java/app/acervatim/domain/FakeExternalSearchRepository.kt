package app.acervatim.domain

import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.repository.ExternalSearchRepository

/**
 * Fake d'[ExternalSearchRepository] : renvoie un résultat programmé, ou une erreur, et enregistre la
 * dernière requête reçue pour les assertions.
 */
class FakeExternalSearchRepository : ExternalSearchRepository {

    var result: Result<List<ExternalMatch>> = Result.success(emptyList())
    var lastBarcode: String? = null
    var lastType: String? = null
    var callCount: Int = 0

    override suspend fun searchByBarcode(barcode: String, type: String): Result<List<ExternalMatch>> {
        callCount++
        lastBarcode = barcode
        lastType = type
        return result
    }
}
