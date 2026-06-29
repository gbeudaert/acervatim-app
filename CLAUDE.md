# CLAUDE.md

Guidance for Claude when working in this Android project (`app.acervatim` — **Acervatim**, application de gestion de collection vinyle, pensée pour évoluer vers d'autres types de collections).

## Stack

- **Language:** Kotlin (JVM target 17, source/target compat 17)
- **UI:** Jetpack Compose + Material 3
- **Build:** Gradle Kotlin DSL (`.kts`) with version catalog (`gradle/libs.versions.toml`)
- **SDK:** `minSdk = 24`, `compileSdk = 34`, `targetSdk = 34`
- **Package:** `app.acervatim`

When upgrading dependencies, edit `gradle/libs.versions.toml` — never hard-code versions in `build.gradle.kts`.

## Build & run

Use the Gradle wrapper. On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug          # build debug APK
.\gradlew.bat installDebug           # install on connected device/emulator
.\gradlew.bat test                   # unit tests
.\gradlew.bat connectedAndroidTest   # instrumentation tests (needs device)
.\gradlew.bat lint                   # Android lint
```

Always invoke `./gradlew` / `.\gradlew.bat`, never a system `gradle`.

## Documentation — où mettre à jour quoi

Trois emplacements distincts, **ne pas les confondre** :

- **Doc destinée à être lue** (utilisateur / développeur / légal) → dépôt **`acervatim-docs`** ([`../acervatim-docs`](../acervatim-docs)), site Jekyll public commun à l'app et au backend, espaces `utilisateur/` · `developpeur/` · `legal/`. Toute nouvelle doc lisible (fonctionnalités, guides, confidentialité…) va **là**, pas sous `docs/`. Le site public historique sous `mvc/docs/*.md` (`index`, `application`, `backend`, `roadmap`, `confidentialite`) est **en cours de migration** vers `acervatim-docs` : ne plus l'enrichir, mettre à jour la page équivalente dans `acervatim-docs`.
- **Sprints** → `docs/interne/` (gitignored) : `sprint-*.md`, `sprint-realises.md`, sprints techniques — **suivi des sprints uniquement**.
- **Matière de travail** → `docs/travail/` (gitignored) : guides d'implémentation (scan/enrichissement), setup OAuth, runbook Play Store, fiches de release, assets. Notes internes, **non publiées**.
- `docs/contexte-global.md` reste un document de travail versionné (contexte app détaillé), exclu du site.

Règle : ne pas recréer sous `docs/` une doc qui a sa place dans `acervatim-docs` ; ne pas publier dans `acervatim-docs` de la matière de travail (brouillons, runbooks, reviews).

## Architecture standards

- **Single-activity + Compose.** `MainActivity` is the only `Activity`; new screens are composables, not new activities or fragments.
- **Layering.** Keep `ui/` (composables, theme, state holders), `data/` (repositories, DTOs, room/network), and `domain/` (models, use cases if needed) separated. Don't let Compose code import from `data/` directly — go through a repository or `ViewModel`.
- **State holders.** Use `ViewModel` + `StateFlow` for screen state. Expose `StateFlow<UiState>` to composables and collect with `collectAsStateWithLifecycle()`. Never put mutable state in composables that should survive configuration changes.
- **Side effects.** Use `LaunchedEffect`, `rememberCoroutineScope`, and `DisposableEffect` correctly — don't launch coroutines in composition bodies.
- **Navigation.** When more than one screen exists, use `androidx.navigation:navigation-compose`. Type-safe routes (Kotlin serialization–based) preferred.
- **DI.** If/when DI is needed, prefer Hilt. Don't introduce a DI framework before there are real dependencies to inject.
- **Concurrency.** Use coroutines + `Flow`. Inject `CoroutineDispatcher`s rather than hard-coding `Dispatchers.IO`.

## Compose conventions

- Composables are `PascalCase` and return `Unit`.
- First parameter after required data is `modifier: Modifier = Modifier`.
- Hoist state: prefer stateless composables that take state + callbacks (`onClick`, `onValueChange`).
- Provide a `@Preview` for any non-trivial composable. Previews must compile without runtime data — pass fakes.
- Use `MaterialTheme.colorScheme` / `.typography` / `.shapes` — never hard-code colors or text styles inside composables. Theme tokens live in `ui/theme/`.
- Use `stringResource(R.string.x)` for any user-visible text. No string literals in UI.

## Kotlin style

- Follow the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html). Prefer `val` over `var`, expression bodies for one-liners, data classes for value types.
- No `!!` operator unless there is a genuine invariant; prefer `requireNotNull` / `checkNotNull` with a message at boundaries.
- No `lateinit` for things that could be constructor-injected.
- Avoid `Any?` and `Map<String, Any>` shapes — model the data.

## Resources

- Strings → `res/values/strings.xml` (and locale variants). No literals in code.
- Colors and dimens may live in `res/` but Compose-only colors should be in `ui/theme/Color.kt`.
- Drawables: prefer vector drawables (`<vector>`) over PNG. The project already enables `vectorDrawables.useSupportLibrary`.
- Asset names: `snake_case`. IDs in XML: `snake_case` (e.g. `@+id/album_list`).

## Permissions & manifest

- Add new permissions to `AndroidManifest.xml` only when actually used. For runtime-permission classes (camera, location, storage on API 33+ media perms), implement the request flow with `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
- Don't request `READ_EXTERNAL_STORAGE` on API 33+ — use the scoped media permissions.
- Network calls require `<uses-permission android:name="android.permission.INTERNET"/>`.

## Testing

- **Unit tests** (`app/src/test`): pure JVM, use JUnit + Turbine for `Flow` + Kotlinx-coroutines-test. ViewModels and pure logic belong here.
- **Instrumentation tests** (`app/src/androidTest`): use Compose UI testing (`createAndroidComposeRule`) for screen-level tests.
- Don't mock what you own when a fake is simpler. Repository interfaces should have hand-written fakes for tests.

## Performance & correctness gotchas

- **Don't block the main thread.** All I/O, DB, and network on a background dispatcher.
- **Lifecycle-aware collection.** Use `collectAsStateWithLifecycle()` not bare `collectAsState()` for `StateFlow`/`Flow` from `ViewModel`.
- **Stable types in Compose.** Mark immutable data classes used in composable params as `@Immutable`/`@Stable` when needed to avoid recomposition.
- **Configuration changes.** Don't store non-serializable state across config changes; let `ViewModel` survive, or use `rememberSaveable`.
- **Leaks.** Don't capture `Context` (especially `Activity`) in long-lived holders. Use `applicationContext` where appropriate; in Compose prefer `LocalContext.current`.

## Release / signing

- Release builds: keep `proguard-android-optimize.txt` + project `proguard-rules.pro`. When enabling `isMinifyEnabled = true`, also enable `isShrinkResources = true` and run `assembleRelease` to verify ProGuard rules.
- Never commit signing keys, keystores, `keystore.properties`, or values in `local.properties`. `local.properties` is machine-local and not source-controlled.

## What NOT to do

- Don't add libraries without a clear need — every dep is binary size and a future upgrade.
- Don't introduce a second `Activity` to "host" a screen; use a composable + navigation.
- Don't write XML layouts for new UI — this is a Compose codebase.
- Don't bump `minSdk` without confirming (it gates which devices can install).
- Don't suppress lint warnings without a comment explaining why.
