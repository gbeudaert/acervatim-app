<!-- bmad:context -->
<!-- Vérifié le 2026-08-22 contre 8797ce5. Géré par bmad-project-context ; les éditions
     à l'intérieur de ce bloc sont remplacées au refresh. Garder hors marqueurs
     ce qui doit survivre. -->

## acervatim-app

Application Android Acervatim (`app.acervatim`) : gestion de collections personnelles,
offline-first — Room est la vérité locale, la sync serveur est optionnelle. Kotlin/Compose,
Gradle KTS avec version catalog. La carte du code et toute la doc lisible vivent dans
`../acervatim-docs`.

## Policy

- Toute la documentation va dans `../acervatim-docs` : ne créer ni référencer aucune doc
  dans ce dépôt. Les sept fichiers encore suivis sous `docs/` sont un vestige à migrer,
  pas un précédent.
- Déclarer les versions de dépendances dans `gradle/libs.versions.toml`, jamais en dur
  dans un `build.gradle.kts`. Pas de nouvelle dépendance sans accord préalable.

## Where things are

- Écrans, use cases, schéma Room, sync : `../acervatim-docs/agent/app/INDEX.md`.
- Lookup par intention : `../acervatim-docs/agent/transverse/ou-trouver.md`.
- Règles communes aux deux dépôts de code : `../acervatim-docs/agent/conventions.md`.

## Conventions that differ from defaults

- Un nouvel écran est un composable plus une route dans `ui/navigation/` — jamais une
  seconde Activity, jamais un layout XML.
- Le code Compose ne consomme jamais `data/` : passer par `domain/`, exposé par un
  `ViewModel`.
- Aucun littéral de texte visible dans l'UI — `stringResource(...)`.
- `Record.genre` est mono-valué alors que le serveur envoie une liste, réduite par
  `data/remote/mapper/FacadeMapper.kt:166`. Ne pas « corriger » en passant : passer au
  pluriel touche `Record`, le formulaire, l'UI et `FacadeMapper` d'un bloc.

## Known pitfalls

- Tests Robolectric qui échouent sur « Some resetters failed » : jamais une régression du
  code, toujours la JVM d'exécution — l'ASM de Robolectric 4.13 ne lit pas les classes d'un
  JDK trop récent. Le fork de test est épinglé au JDK 21 (`javaLauncher` dans
  `app/build.gradle.kts`) ; si l'erreur revient, vérifier que l'épinglage tient et qu'un
  JDK 21 est bien détecté (`./gradlew javaToolchains`) avant de chercher ailleurs.

<!-- /bmad:context -->
