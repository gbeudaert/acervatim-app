package app.acervatim.domain

import app.acervatim.domain.model.EditionMapping
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

    /** Programmable pour les tests d'import de série ; échec par défaut (aucun mapping). */
    var editionMappingResult: Result<EditionMapping> =
        Result.failure(IllegalStateException("no mapping configured"))
    var lastEditionTitle: String? = null
    var lastEditionEdition: String? = null
    var editionMappingCallCount: Int = 0

    override suspend fun searchByBarcode(barcode: String, type: String): Result<List<ExternalMatch>> {
        callCount++
        lastBarcode = barcode
        lastType = type
        return result
    }

    override suspend fun editionMapping(titleFr: String, edition: String?): Result<EditionMapping> {
        editionMappingCallCount++
        lastEditionTitle = titleFr
        lastEditionEdition = edition
        return editionMappingResult
    }
}
