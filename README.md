# Acervatim

> *acervatim* (lat.) — en monceaux, par accumulation. De *acervus* « tas, amas, collection ».

Application Android open source pour cataloguer vos collections. Démarre par les **disques vinyles**, conçue pour s'étendre à d'autres types de collections (livres, jeux, BD…).

![Statut](https://img.shields.io/badge/statut-pre--alpha-orange)
![Plateforme](https://img.shields.io/badge/platform-Android%2024%2B-blue)
![Licence](https://img.shields.io/badge/license-Apache%202.0-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin)

---

## Captures d'écran

> *À ajouter dès que les écrans principaux sont stabilisés.*

| Liste | Détail | Ajout |
|:---:|:---:|:---:|
| _placeholder_ | _placeholder_ | _placeholder_ |

---

## Statut

**Pre-alpha — en construction.** Aucune release publiée, le schéma de base de données et les API internes peuvent encore changer sans préavis. Pas prêt pour un usage quotidien.

Suivez l'avancement via les *issues* et la [roadmap](#roadmap).

---

## Pourquoi Acervatim ?

- **100 % hors-ligne** par défaut : vos données restent sur votre appareil, aucune télémétrie, aucun compte.
- **Multi-collections** : la même app sert demain à gérer vinyles, livres, jeux vidéo, BD… Le modèle de données est pensé pour ça.
- **Material You** : thème seedé depuis la couleur de marque, *light* et *dark* dynamiques.
- **Libre** (Apache 2.0) — auditez, forkez, contribuez.

---

## Deux phases de fonctionnement

Acervatim est conçu autour de deux modes complémentaires.

### 1. Mode hors-ligne (par défaut, MVP)

Tout fonctionne sans connexion. L'utilisateur saisit manuellement les fiches :

- Ajout, édition, suppression de disques
- Photo de jaquette (appareil photo ou galerie), notes personnelles, statut (possédé / wishlist), date de dernière écoute
- Recherche instantanée, filtres (genre, format, statut)
- Import / export local en CSV / JSON
- Aucune permission réseau requise pour ce mode

C'est le scénario garanti : Acervatim reste utilisable même sans internet, sans compte, sans serveur.

### 2. Mode en ligne (planifié, opt-in)

Pour réduire la saisie manuelle, un **wrapper de providers de métadonnées** sera ajouté, avec une API spécialisée par type de collection :

| Collection | Provider envisagé |
|---|---|
| Vinyles / CD | Discogs, MusicBrainz |
| Livres | Open Library, Google Books |
| Jeux vidéo | IGDB |
| BD | BDovore, ComicVine |

L'utilisateur active explicitement un provider (token API si nécessaire), scanne un code-barres ou saisit un identifiant, et la fiche se pré-remplit. **Rien n'est envoyé sans action explicite de l'utilisateur.**

L'architecture est déjà préparée côté `domain/provider/` (`MetadataProvider`, `ProviderRegistry`, `AuthStore`) — l'implémentation concrète arrive après la stabilisation du MVP hors-ligne.

---

## Stack

| Couche | Choix |
|---|---|
| Langage | Kotlin (JVM 17) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture *light* (ui / domain / data) |
| DI | Hilt |
| Navigation | Navigation Compose |
| Persistance | Room (SQLite, `Flow` réactifs) |
| Images | Coil 3 + stockage local (`filesDir/covers/`) |
| Concurrence | kotlinx.coroutines, `StateFlow` |
| Build | Gradle Kotlin DSL + version catalog |
| SDK | `minSdk 24` · `compileSdk 34` · `targetSdk 34` |

Versions précises : voir [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

---

## Architecture en bref

```
app/src/main/java/fr/beudaert/acervatim/
├── di/                # Modules Hilt
├── data/              # Room, stockage local, providers réseau
├── domain/            # Modèles, usecases, contrats provider
└── ui/                # Compose : screens, components, thème
```

**Règle d'or :** la couche `ui/` n'importe jamais `data/` directement — tout passe par un usecase. Pas de XML pour les nouveaux écrans, pas de `LiveData`, pas de `!!`. Détails dans [CLAUDE.md](CLAUDE.md).

---

## Démarrage rapide

### Pré-requis

- **JDK 17** (`JAVA_HOME` configuré)
- **Android SDK** via Android Studio ou les *command-line tools* (`ANDROID_HOME` défini)
- Un fichier `local.properties` à la racine (non versionné) :
  ```properties
  sdk.dir=C:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
  ```

### Cloner et lancer (Windows / PowerShell)

```powershell
git clone https://github.com/<user>/acervatim.git
cd acervatim
.\gradlew.bat assembleDebug          # build APK debug
.\gradlew.bat installDebug           # installer sur appareil/émulateur
.\gradlew.bat test                   # tests unitaires
.\gradlew.bat connectedAndroidTest   # tests instrumentés (appareil requis)
.\gradlew.bat lint                   # Android lint
```

Sur macOS / Linux, remplacez par `./gradlew`. Le moyen le plus simple reste **Android Studio Hedgehog ou plus récent** : *Open project* sur la racine, attendre la sync Gradle, *Run app*.

> Le wrapper Gradle n'est pas commité. S'il est absent, Android Studio le génère à l'ouverture, ou exécutez une fois `gradle wrapper --gradle-version 8.9`.

---

## Roadmap

Étapes prévues, par ordre approximatif :

1. **Fondations** — thème seedé, Room, écran liste, ajout manuel ✅
2. **Expérience liste** — recherche, filtres, sticky headers, wishlist grisée, écran détail
3. **Médias** — `CoverStorage`, prise photo, import galerie, placeholders Coil
4. **Multi-artistes & métadonnées étendues** — table `artists` (n-n), vitesse de gravure
5. **Mode en ligne — vinyles** — scan code-barres (ML Kit), provider Discogs, import/export CSV+JSON
6. **Mode en ligne — autres collections** — providers livres / jeux, sélection du type de collection à l'install
7. **Polish & release** — animations partagées, tests UI, accessibilité, préparation Play Store

Le détail de la publication Play Store est documenté dans [`docs/playstore-deployment.md`](docs/playstore-deployment.md).

---

## Conventions

- **Compose** — composables `PascalCase`, premier paramètre optionnel = `modifier: Modifier = Modifier`, stateless préférés (state hoisté), `@Preview` *light* + *dark* pour tout composant non trivial.
- **Strings** — tout dans `res/values/strings.xml` (FR par défaut, EN dans `values-en/`). Jamais de littéral dans l'UI.
- **Thème** — uniquement `MaterialTheme.colorScheme` / `.typography` / `.shapes`. Pas de couleurs codées en dur.
- **State** — `ViewModel` + `StateFlow<UiState>`, consommé via `collectAsStateWithLifecycle()`.
- **Tests** — JUnit + Turbine + `kotlinx-coroutines-test` côté domain ; `createAndroidComposeRule` côté UI ; Robolectric quand un contexte Android est nécessaire en JVM.
- **Commits** — [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`…).

---

## Contribuer

Le projet est en pre-alpha — la meilleure manière d'aider aujourd'hui est :

- **Ouvrir une issue** pour signaler un bug, proposer une amélioration, ou discuter d'un type de collection que vous aimeriez voir supporté.
- **Tester une build locale** et remonter les frictions UX.
- **Proposer une PR** ciblée. Les changements structurels (nouvelle dépendance, nouvelle couche) gagnent à être discutés dans une issue avant.

Avant de soumettre une PR, lancez `./gradlew lint test` et assurez-vous que tout passe.

---

## Sécurité

Ne **jamais** commiter : `local.properties`, `keystore.properties`, fichiers `.jks` / `.keystore`, tokens API. Les tokens utilisateur (Discogs et autres providers) seront stockés via `EncryptedSharedPreferences`.

Une vulnérabilité ? Ouvrez une issue *Security* ou contactez le mainteneur en privé avant toute divulgation publique.

---

## Licence

Distribué sous licence **Apache 2.0** — voir le fichier [LICENSE](LICENSE) (à ajouter à la racine).

```
Copyright 2026 Guillaume Beudaert

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
