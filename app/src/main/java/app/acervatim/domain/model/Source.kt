package app.acervatim.domain.model

/**
 * Source de données externe interrogée (via le backend) pour enrichir un type de collection.
 *
 * [providerCode] est aligné sur le contrat OAuth backend (`/v1/oauth/:provider/…`). Les sources
 * publiques ([requiresToken] `false`, ex. BnF) ne demandent aucun jeton : elles sont listées pour
 * l'utilisateur mais ne proposent pas de connexion.
 *
 * L'app n'appelle **jamais** une source directement : connexion, enrichissement et recherche
 * transitent tous par le backend, qui conserve les jetons chiffrés. L'app n'expose que l'état
 * « connecté / à connecter ».
 */
enum class Source(
    val providerCode: String,
    val requiresToken: Boolean
) {
    DISCOGS(providerCode = "discogs", requiresToken = true),
    MYANIMELIST(providerCode = "mal", requiresToken = true),
    BNF(providerCode = "bnf", requiresToken = false),

    /**
     * Source principale des séries manga (identité, synopsis FR, jaquettes, recherche par titre),
     * publique et sans jeton. Listée pour être **créditée** dans l'app — sa politique d'usage l'exige.
     */
    MANGADEX(providerCode = "mangadex", requiresToken = false);

    companion object {
        fun fromProviderCode(code: String): Source? = entries.firstOrNull { it.providerCode == code }
    }
}

/**
 * Sources dont dépend un [CollectionType]. Mapping déclaratif : un nouveau type n'a qu'à déclarer
 * ses sources ici, l'écran « Gérer les collections » s'adapte sans autre changement.
 */
val CollectionType.sources: List<Source>
    get() = when (this) {
        CollectionType.VINYL -> listOf(Source.DISCOGS)
        CollectionType.MANGA -> listOf(Source.MANGADEX, Source.BNF, Source.MYANIMELIST)
    }
