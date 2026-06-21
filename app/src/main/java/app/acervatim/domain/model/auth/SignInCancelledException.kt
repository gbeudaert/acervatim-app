package app.acervatim.domain.model.auth

/** L'utilisateur a fermé la feuille de connexion Google : à distinguer d'un véritable échec. */
class SignInCancelledException : Exception()
