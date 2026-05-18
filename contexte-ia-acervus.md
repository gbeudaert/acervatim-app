# Contexte IA — Acervus

> Document de référence destiné à un assistant IA développeur (Claude Code, Cursor, etc.) pour générer, faire évoluer et maintenir **Acervus**, une application Android multi-collections.

**Étymologie.** *Acervus* (latin, masc., 2ᵉ déclinaison) : « tas, monceau, amas » — par extension, « accumulation, multitude ». Donne aussi son nom au *paradoxe sorite* en logique : à partir de combien de grains a-t-on un tas ?

**Package applicatif.** `fr.beudaert.acervus` (reverse-DNS sur domaine personnel possédé). Le nom du package est immuable une fois publié sur le Play Store.

---

## 1. Vision produit

**Acervus** est une application Android **100% offline, sans backend** (au démarrage), permettant à un collectionneur de gérer plusieurs types de collections dans une expérience unifiée. La première collection cible (MVP) est le **vinyle**, mais l'architecture est conçue dès le départ pour accueillir LEGO, jeux de société, mangas, BD, livres, jeux vidéo, films, etc.

L'expérience visée s'inspire de l'**application Google Contacts (Android)** : une longue liste sobre, scannable, avec recherche instantanée via une SearchBar flottante et filtres avancés.

### Objectifs fonctionnels (MVP — collection vinyle)
1. Visualiser toute la collection sous forme de liste unique.
2. Maintenir une **wishlist** : disques désirés mais non possédés, affichés **grisés** dans la liste principale.
3. Consulter le **détail d'un disque** (fiche complète).
4. **Ajouter** un disque via plusieurs modes (manuel, scan code-barres, import CSV/JSON, API Discogs).
5. **Rechercher** par texte libre.
6. **Filtrer** via panneau avancé (wishlist on/off, genre, format, etc.).
7. Marquer une **dernière écoute** avec bouton reset.

### Objectifs structurels (transverses, à honorer dès le MVP)
- Architecture **multi-collections** : un `CollectionType` (VINYL/LEGO_SET/BOARD_GAME/MANGA/COMIC/…) est porté par chaque objet.
- Abstraction **MetadataProvider** pour les sources externes, conçue pour chaîner plusieurs providers et basculer vers un backend plus tard.
- Modèle de données **agnostique** : champs communs en première classe, spécificités dans `extras: Map<String,String>`.

### Non-objectifs (hors MVP)
- Synchronisation cloud / multi-appareils.
- Partage social, communauté, marketplace.
- Lecture audio.
- Comptes utilisateurs.
- Implémentation effective des collections autres que vinyle (l'architecture les anticipe, le code MVP ne les expose pas).

---

## 2. Stack technique

| Couche | Choix | Justification |
|---|---|---|
| Langage | **Kotlin** (latest stable) | Standard Android moderne |
| UI | **Jetpack Compose** + Material 3 | Déclaratif, animations fluides, thème dynamique |
| Architecture | **MVVM + Clean Architecture light** | Séparation UI / domaine / data |
| DI | **Hilt** | Standard Google, intégration Compose |
| Navigation | **Navigation Compose** | Type-safe routes |
| Persistance | **Room** (SQLite) | Offline-first, requêtes réactives via Flow |
| Stockage images | Filesystem app (`filesDir/covers/`) | Pas de blobs en BDD |
| Chargement images | **Coil 3** | Compose-native, cache disque |
| Scan code-barres | **ML Kit Barcode Scanning** (offline) | Bundled model, pas de réseau requis |
| API externe (optionnel) | **Discogs API** via Retrofit + OkHttp | Récupération métadonnées + jaquettes |
| Sérialisation | **kotlinx.serialization** | Import/export CSV+JSON |
| Tests | JUnit5, Turbine, Compose UI Test, Robolectric | Couverture unit + UI |
| Min SDK / Target SDK | minSdk 26 / targetSdk 35 | Couvre ~95% du parc, APIs modernes |

### Dépendances clés (extrait `libs.versions.toml`)
```
androidx-compose-bom = "2025.01.00"
room = "2.6.1"
hilt = "2.52"
coil = "3.0.4"
mlkit-barcode = "17.3.0"
retrofit = "2.11.0"
kotlinx-serialization-json = "1.7.3"
navigation-compose = "2.8.5"
```

---

## 3. Architecture

```
app/
├── di/                       # Modules Hilt
├── data/
│   ├── local/
│   │   ├── db/               # RoomDatabase, DAO, Entities
│   │   ├── storage/          # CoverStorage (filesystem)
│   │   └── importexport/     # CSV/JSON readers & writers
│   ├── remote/
│   │   ├── discogs/          # DiscogsApi, DTOs
│   │   └── barcode/          # BarcodeScanner wrapper ML Kit
│   └── repository/           # RecordRepositoryImpl
├── domain/
│   ├── model/                # Record, RecordStatus, Filter…
│   ├── repository/           # RecordRepository (interface)
│   └── usecase/              # AddRecord, ToggleWishlist, ResetLastPlayed…
├── ui/
│   ├── theme/                # Couleurs, typo, shapes Material3
│   ├── components/           # RecordRow, SearchBar, FilterSheet…
│   ├── list/                 # CollectionScreen + ViewModel
│   ├── detail/               # RecordDetailScreen + ViewModel
│   ├── edit/                 # AddEditRecordScreen + ViewModel
│   └── scan/                 # BarcodeScanScreen
└── MainActivity.kt
```

### Règles d'architecture
- **UI ne connaît jamais Room/Retrofit** : passe par les usecases.
- Tous les flux de lecture sont des `Flow<T>` exposés par le repository.
- Les usecases sont des classes simples avec un seul `operator fun invoke(...)`.
- Pas de `LiveData`, uniquement `StateFlow` côté ViewModel.
- L'API Discogs est **optionnelle** : l'app fonctionne entièrement sans connexion, l'API n'est sollicitée que lors d'ajouts explicites.

---

## 4. Modèle de données

### Entité `Record`
```kotlin
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val id: String,           // UUID
    val artist: String,                   // Auteur (obligatoire)
    val title: String,                    // Nom album (obligatoire)
    val genre: String?,                   // Genre principal
    val year: Int?,                       // Année de sortie
    val label: String?,                   // Label / édition
    val format: VinylFormat?,             // 33t / 45t / 78t / autre
    val country: String?,                 // Pays de pressage (ISO ou libre)
    val purchasePrice: Double?,           // Prix d'achat (devise locale)
    val rating: Int?,                     // 0..5
    val coverPath: String?,               // Chemin local du fichier image
    val coverRemoteUrl: String?,          // URL Discogs (pour re-cache)
    val barcode: String?,                 // EAN/UPC scanné
    val discogsId: Long?,                 // ID Discogs si importé
    val status: RecordStatus,             // OWNED ou WISHLIST
    val lastPlayedAt: Instant?,           // Nullable, reset = null
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class VinylFormat { LP_33, SINGLE_45, SHELLAC_78, OTHER }
enum class RecordStatus { OWNED, WISHLIST }
```

### Index Room recommandés
- Index sur `artist`, `title`, `status` pour accélérer recherche/filtres.
- Index unique sur `barcode` (nullable) pour éviter doublons au scan.

### DAO — méthodes principales
```kotlin
@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("""
        SELECT * FROM records
        WHERE (:query = '' OR artist LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')
          AND (:statusFilter IS NULL OR status = :statusFilter)
          AND (:genreFilter IS NULL OR genre = :genreFilter)
          AND (:formatFilter IS NULL OR format = :formatFilter)
        ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE
    """)
    fun observeFiltered(
        query: String,
        statusFilter: RecordStatus?,
        genreFilter: String?,
        formatFilter: VinylFormat?
    ): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id")
    fun observeById(id: String): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): RecordEntity?

    @Upsert suspend fun upsert(record: RecordEntity)
    @Delete suspend fun delete(record: RecordEntity)
    @Query("UPDATE records SET lastPlayedAt = :ts WHERE id = :id")
    suspend fun updateLastPlayed(id: String, ts: Instant?)
}
```

---

## 5. Spécifications UI

### Thème

**Approche : Material You "seedé" autour d'un vert d'eau de marque.** On ne code pas une palette fixe à la main, on laisse Material générer une palette mathématiquement harmonisée (contrastes WCAG garantis, tons dérivés cohérents) à partir d'un **seed color** imposé. L'utilisateur peut basculer en Material You pur (wallpaper dynamique) s'il le souhaite.

#### Seed color
```kotlin
val BrandSeed = Color(0xFF5BA199)   // Vert d'eau Acervus
```

#### Trois modes proposés (réglage utilisateur)

| Mode | Seed | PaletteStyle | Disponibilité |
|---|---|---|---|
| **Vert d'eau** (défaut) | `BrandSeed` | `Fidelity` | Toutes versions |
| **Adapté au système** | Wallpaper dynamique | `TonalSpot` (défaut Android) | Android 12+ uniquement |
| **Expressif** | `BrandSeed` | `Expressive` | Toutes versions |

`Fidelity` est volontairement choisi pour le mode par défaut : il garde le seed très reconnaissable dans la palette générée, contrairement à `TonalSpot` qui peut s'en éloigner notablement.

#### Implémentation

Utiliser la lib officielle Google `material-color-utilities` (déjà transitive via `androidx.compose.material3`) pour générer dynamiquement la `ColorScheme` :

```kotlin
// build.gradle.kts
implementation("com.google.android.material:material:1.12.0")
// material-color-utilities est inclus

// ui/theme/Theme.kt
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.Hct

enum class ThemeMode { BRAND, SYSTEM_DYNAMIC, EXPRESSIVE }

@Composable
fun AcervusTheme(
    mode: ThemeMode = ThemeMode.BRAND,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        mode == ThemeMode.SYSTEM_DYNAMIC && supportsDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        mode == ThemeMode.EXPRESSIVE ->
            buildSchemeFromSeed(BrandSeed, darkTheme, expressive = true)

        else ->
            buildSchemeFromSeed(BrandSeed, darkTheme, expressive = false)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AcervusTypography,
        shapes = AcervusShapes,
        content = content
    )
}

private fun buildSchemeFromSeed(
    seed: Color,
    dark: Boolean,
    expressive: Boolean,
    contrastLevel: Double = 0.0
): ColorScheme {
    val hct = Hct.fromInt(seed.toArgb())
    val scheme: DynamicScheme = if (expressive)
        SchemeExpressive(hct, dark, contrastLevel)
    else
        SchemeFidelity(hct, dark, contrastLevel)

    return scheme.toComposeColorScheme()   // helper de mapping → ColorScheme Material3
}
```

> Le mapping `DynamicScheme → ColorScheme` est une extension à écrire une fois (≈30 lignes), récupérant chaque rôle via `MaterialDynamicColors.primary().getArgb(scheme)`, etc.

#### Règles d'usage de la palette générée

Quel que soit le mode actif, la sémantique d'usage reste constante :

- L'accent (`primary`) est utilisé avec parcimonie : FAB, état sélectionné, étoiles de note, switch actif, indicateur "écouté récemment". Jamais sur de larges aplats.
- Les **jaquettes** sont les éléments visuels principaux — la palette doit rester en retrait pour les laisser respirer.
- **Élévation** : séparateurs fins (`outline` à 30% d'opacité) plutôt qu'ombres marquées. Esthétique plate proche de Google Contacts.
- **Coins arrondis** : 12dp cards, 8dp chips, 4dp jaquettes miniatures, 24dp FAB et bottom sheets.

#### Typographie
- Police : `Inter` (embarquée) ou système (`SansSerif`) en fallback.
- Titres en `Medium`, corps en `Regular`, métadonnées en `Regular` à 13sp couleur `onSurfaceVariant`.

#### Grisage wishlist (cohérent palette)
- **Light** : `alpha = 0.45` sur la ligne entière + icône cœur en `primary` à `alpha = 0.7`.
- **Dark** : `alpha = 0.40` (le sombre absorbe plus). Même icône cœur en `primary`.
- Le grisage ne doit **jamais** être la seule indication : l'icône cœur garantit l'accessibilité (daltoniens, TalkBack).

#### Persistance du choix utilisateur
- Stocker `ThemeMode` dans `DataStore Preferences` (clé `theme_mode`).
- Exposer via un `SettingsRepository` → consommé par le `MainViewModel` qui pilote `AcervusTheme`.
- Changement appliqué instantanément (recomposition).

### Écran 1 — Liste collection (écran d'accueil)

**Inspiration : Google Contacts (Android).** L'app Contacts Android repose sur une `SearchBar` Material3 flottante qui sert à la fois de barre de recherche et de top bar, des sections alphabétiques, une `LazyColumn` dense, et un FAB rond en bas à droite. C'est exactement ce qu'on reproduit.

Structure verticale :
1. **`SearchBar` Material3** (composant officiel, pas un `TextField`) flottante en haut, avec marges latérales :
   - Avatar/icône à gauche (logo app ou menu hamburger).
   - Placeholder "Rechercher dans ma collection".
   - Bouton **filtres** (icône `tune`) à droite, intégré dans la SearchBar.
   - Quand tapée, elle s'étend en plein écran avec suggestions / résultats live (comportement natif du composant).
2. **Bandeau compteur** discret sous la SearchBar : `128 disques · 12 souhaités` en `bodySmall` couleur `onSurfaceVariant`. Disparaît au scroll.
3. **`LazyColumn`** des disques :
   - Sectionnée par première lettre de l'artiste (`stickyHeader` Compose) : lettre seule, fond `surface`, `labelLarge` en `primary`, façon Google Contacts.
   - Chaque ligne `RecordRow` (hauteur ~72dp, padding horizontal 16dp) :
     - Jaquette miniature à gauche, **48dp**, coins arrondis 4dp, placeholder vinyle si absente.
     - Bloc texte central : titre album (`bodyLarge`) sur ligne 1, artiste (`bodyMedium`, `onSurfaceVariant`) sur ligne 2.
     - Badge format discret à droite (`labelSmall` dans une puce `surfaceVariant`).
     - Si `status == WISHLIST` → `alpha = 0.45` (light) / `0.40` (dark) sur toute la ligne + icône cœur (`Icons.Outlined.FavoriteBorder`) en bout de ligne, `primary` à `alpha 0.7`.
   - Pas de séparateurs visibles entre lignes (style Contacts Android = lignes "aérées" sans divider).
4. **FAB étendu** en bas à droite : icône `+` + label "Ajouter" (se rétracte en FAB rond au scroll vers le bas, étendu au scroll vers le haut — pattern Material3). Tap → bottom sheet d'ajout (Manuel / Scan / Import / Discogs).
5. **Index alphabétique** sur le bord droit (fast scroller), optionnel v2 — Google Contacts l'a abandonné, on peut s'en passer.

**Filtres avancés (BottomSheet)** :
- Toggle "Inclure la wishlist" / "Wishlist uniquement" / "Collection uniquement" (3 états).
- Dropdown genre (valeurs distinctes calculées).
- Dropdown format.
- Toggle "Non écoutés depuis 6 mois".
- Bouton "Réinitialiser" + bouton "Appliquer".

### Écran 2 — Détail disque

- **Header** : grande jaquette (carré, 80% largeur écran, ombre douce).
- Sous la jaquette : artiste (`headlineSmall`) + titre (`titleLarge`).
- **Note** : rangée de 5 étoiles éditables au tap.
- **Chips d'infos** : genre, format, année, label, pays, prix.
- **Section "Dernière écoute"** :
  - Texte : "Il y a 3 jours" (relatif) + date complète en dessous.
  - Bouton `OutlinedButton` "Je viens de l'écouter" → set `lastPlayedAt = now`.
  - Bouton `TextButton` rouge "Reset" → set `lastPlayedAt = null`.
- **Section "Statut"** : Switch "Dans ma wishlist" pour basculer OWNED ⇄ WISHLIST.
- **Menu overflow** (3 points) : Éditer, Supprimer, Voir sur Discogs (si `discogsId` présent).

### Écran 3 — Ajout / Édition

Formulaire scrollable avec sections :
1. **Jaquette** : zone tappable → choix entre "Prendre une photo" / "Choisir dans galerie" / "Rechercher sur Discogs".
2. **Identité** : artiste *, titre *, année.
3. **Édition** : label, format (segmented button), pays.
4. **Classification** : genre (autocomplete sur valeurs existantes), note.
5. **Achat** : prix, code-barres (avec bouton scan).
6. **Statut** : toggle wishlist.
7. Boutons "Annuler" / "Enregistrer" en bas (sticky).

### Écran 4 — Scan code-barres

- Pleine page caméra (`CameraX` + ML Kit).
- Overlay avec viseur rectangulaire.
- Au scan réussi :
  - Si `barcode` existe déjà en base → snackbar "Disque déjà présent" + navigation vers le détail.
  - Sinon → si connexion dispo, requête Discogs `database/search?barcode=…`. Pré-remplit le formulaire d'ajout.
  - Sinon → formulaire vide avec le code-barres pré-rempli.

---

## 6. Logique métier — règles

### Wishlist
- Un disque a un statut unique : `OWNED` ou `WISHLIST`. Pas les deux.
- Dans la liste principale, les deux sont visibles par défaut, les WISHLIST avec `alpha = 0.45`.
- Le filtre permet d'isoler l'un ou l'autre.

### Dernière écoute
- `lastPlayedAt` est un `Instant?`. `null` = jamais écouté ou reset.
- Affichage relatif : `< 1h` → "À l'instant" ; `< 24h` → "Aujourd'hui à HH:mm" ; `< 7j` → "Il y a N jours" ; sinon date complète.
- Reset = `UPDATE records SET lastPlayedAt = NULL`.

### Doublons
- Au scan, déduplication stricte sur `barcode`.
- À la saisie manuelle, alerter si `(artist, title)` existe déjà (case-insensitive) sans bloquer.

### Import / Export
- **Export** : tous les disques en JSON (préféré) ou CSV. Inclut champs + chemin relatif des covers. ZIP optionnel avec dossier `covers/`.
- **Import** : valide le schéma, fusionne par `id` ou crée. Photos remplacées si présentes.

### Collections supportées et modes de référencement

Acervus est conçue pour gérer plusieurs types de collections. Chaque type a un **mode de référencement par défaut** qui détermine comment les objets sont affichés et comptés, mais l'utilisateur peut basculer entre les modes via un toggle dans l'écran de liste.

#### Les deux modes

**Mode "plat" (FLAT)** — un objet de la collection = une entrée dans la liste.
- Liste linéaire de tous les items individuels.
- Convient quand chaque pièce a une identité forte autonome (vinyle, set LEGO, jeu de société, album de BD).
- C'est l'expérience par défaut héritée du MVP vinyle.

**Mode "série" (SERIAL)** — une série = une entrée dans la liste, qui regroupe ses volumes.
- Liste des séries possédées/suivies, chaque ligne affichant la progression (`Berserk 28/42`).
- Tap sur une série → écran dédié avec la grille des tomes.
- Convient quand l'objet collectionné a une forte structure sérielle (manga, BD série longue).

#### Mode par défaut et bascule par type

| Type de collection | Mode par défaut | Bascule autorisée | Justification |
|---|---|---|---|
| `VINYL` | FLAT | non (FLAT uniquement) | Pas de notion de série dominante. |
| `LEGO_SET` | FLAT | non (FLAT uniquement) | Les "thèmes" existent mais ne sont pas des séries fermées. |
| `BOARD_GAME` | FLAT | non (FLAT uniquement) | Les extensions sont rattachées au jeu de base via une autre logique. |
| `MANGA` | **SERIAL** | oui, bascule possible vers FLAT | L'utilisateur suit majoritairement des séries (Berserk, One Piece). Vue à plat utile pour les one-shots ou pour voir le détail de chaque tome. |
| `COMIC` (BD) | **FLAT** | oui, bascule possible vers SERIAL | Beaucoup de BD sont des albums autonomes ou des séries courtes. Vue série utile pour les longues séries franco-belges (Tintin, Astérix, XIII, Largo Winch). |
| `BOOK` (anticipation) | FLAT | oui | À voir selon usage. |

Le mode est stocké dans `DataStore Preferences` par `CollectionType`, indépendamment pour chaque collection active.

#### Vue série (SERIAL view) — règles d'affichage

Quand l'utilisateur est en vue série pour un type donné, la liste principale affiche des **lignes de série** :

```
┌──────────────────────────────────────────────┐
│ [cover tome 1]  Berserk                      │
│                 Kentaro Miura · seinen       │
│                 28 / 42 tomes possédés       │
└──────────────────────────────────────────────┘
```

- La cover affichée est par défaut celle du tome 1 (ou tome représentatif configurable).
- Le compteur affiche `possédés / total_connu_éditeur` (le total peut être inconnu si série en cours → `28 / ?`).
- Si un ou plusieurs tomes de la série sont en wishlist → badge cœur discret en bout de ligne.

Tap sur une ligne → **écran "Détail série"**.

#### Écran Détail série (vue SERIAL)

Affichage en grille (ou liste verticale dense) de **tous les tomes connus** de la série, possédés ou non. Chaque tome est représenté par un tile :

- Cover du tome (ou placeholder si pas connue).
- Numéro de tome bien visible (`#12`).
- État visuel de possession (cf. ci-dessous).

#### Les trois états de possession (vue SERIAL)

Pour chaque tome d'une série, l'utilisateur peut basculer entre trois états via tap long ou menu contextuel sur le tile :

| État | Code | Affichage |
|---|---|---|
| **Possédé** | `OWNED` | Tile pleine couleur, cover normale, petit check vert/primary en coin. |
| **Wishlist** | `WISHLIST` | Tile **grisée** (alpha 0.45 light / 0.40 dark) + icône cœur en surimpression — cohérent avec le grisage de la liste principale. |
| **Ignoré** | `IGNORED` | Tile très atténuée (alpha 0.2), grisée monochromatique, sans icône. Pas dans la collection, pas dans la wishlist : "je sais qu'il existe, je ne le veux pas". |

L'état par défaut d'un tome jamais touché est **implicitement `IGNORED`** : la base ne stocke que les tomes que l'utilisateur a explicitement classés (économie de stockage, et l'utilisateur ne veut pas voir 1 200 lignes IGNORED qu'il n'a pas créées).

→ Conséquence : un tome inconnu de la base locale mais présent dans le catalogue série affiché provient des données du provider et est rendu visuellement comme IGNORED, sans créer d'entrée Room. L'entrée est créée à la première action utilisateur (basculement vers OWNED ou WISHLIST).

#### Impact sur le modèle de données

L'entité `Record` (renommée à terme `CollectionItem` pour refléter la généricité) doit porter :
- `collectionType: CollectionType` — type de la collection (VINYL, MANGA, COMIC…).
- `seriesId: String?` — référence à la série mère, null pour les items autonomes (vinyle, LEGO, jeu) ou les one-shots.
- `volumeNumber: Int?` — numéro de tome dans la série (`12` pour `Berserk T.12`), null si non sériel.
- `status` étendu pour intégrer l'état IGNORED en plus de OWNED / WISHLIST. Voir ci-dessous.

```kotlin
enum class ItemStatus { OWNED, WISHLIST, IGNORED }
```

Évolution rétro-compatible :
- L'enum existant `RecordStatus { OWNED, WISHLIST }` est renommé `ItemStatus` et reçoit la valeur supplémentaire `IGNORED`.
- En vue FLAT (vinyle, BD à plat), seuls OWNED et WISHLIST sont utilisés ; IGNORED est invisible (et de toute façon les items IGNORED ne sont pas persistés sauf bascule explicite).
- Une migration Room est requise pour autoriser la nouvelle valeur (l'enum est stocké en String, donc la migration est triviale : juste un `@TypeConverter` mis à jour, pas de modif schéma).

Table dédiée pour les séries (anticipation) :

```kotlin
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,                  // ex: "anilist:30002" ou "googlebooks:xyz"
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String?,                 // mangaka, scénariste principal
    val coverUrl: String?,                       // cover représentative (souvent tome 1)
    val knownVolumeCount: Int?,                  // null si inconnu / en cours
    val status: SeriesPublicationStatus,         // ONGOING, COMPLETED, CANCELLED, UNKNOWN
    val genres: List<String>,
    val synopsis: String?,
    val providerId: String,
    val updatedAt: Instant
)

enum class SeriesPublicationStatus { ONGOING, COMPLETED, CANCELLED, UNKNOWN }
```

La table `series` n'est peuplée que lorsqu'une série est concernée par au moins un item (OWNED ou WISHLIST). Pas de pré-chargement de catalogues entiers.

#### Impact UI synthétique

- L'écran "Liste collection" (§5, Écran 1) reçoit un **toggle de vue** dans les filtres avancés : `Vue plate / Vue série` (visible uniquement pour les types autorisant la bascule).
- Quand un type est en vue série, les `RecordRow` deviennent `SeriesRow` et la navigation va vers l'écran "Détail série" plutôt que "Détail item".
- L'écran "Détail item" reste accessible : depuis la grille des tomes d'une série, tap court sur un tome OWNED ouvre son détail.

### API Discogs et abstraction "MetadataProvider"

L'app est pensée pour évoluer vers **plusieurs types de collections** (vinyles, mais demain BD, livres, jeux vidéo, films…), chacune pouvant avoir **une ou plusieurs API de référence** pour enrichir les métadonnées. L'intégration Discogs n'est donc pas une dépendance directe : c'est la **première implémentation** d'une abstraction générique `MetadataProvider`.

#### Principes

1. **Aucun couplage** entre la couche domaine et un fournisseur précis.
2. **Provider-agnostic** : ajouter Spotify, MusicBrainz, BookBrainz, IGDB, TMDB plus tard ne doit rien changer ailleurs que dans la couche `data/remote/providers/`.
3. **Découverte dynamique** : la liste des providers actifs pour une collection donnée est résolue à l'exécution (Hilt multi-binding).
4. **Auth pluggable** : chaque provider gère sa propre stratégie (token user, OAuth, clé API, anonyme).
5. **Backend-ready** : l'abstraction est conçue pour que basculer un provider de "in-app direct" à "via mon backend" soit un changement d'implémentation, pas une refonte.

#### Interfaces domaine

```kotlin
// domain/model/CollectionType.kt
enum class CollectionType { VINYL, BOOK, COMIC, GAME, MOVIE /* … */ }

// domain/model/MetadataQuery.kt
sealed interface MetadataQuery {
    data class ByBarcode(val code: String) : MetadataQuery
    data class ByText(val query: String, val type: CollectionType) : MetadataQuery
    data class ById(val providerId: String, val externalId: String) : MetadataQuery
}

// domain/model/MetadataResult.kt
// Modèle générique, agnostique du type de collection.
data class MetadataResult(
    val providerId: String,          // ex: "discogs"
    val externalId: String,          // ID dans le système du provider
    val collectionType: CollectionType,
    val title: String,
    val primaryAuthor: String?,      // artiste / auteur / réalisateur / studio
    val year: Int?,
    val coverUrl: String?,
    val genres: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap()  // champs spécifiques type collection
)

// extras pour un vinyle : "label", "format", "country", "catalogNumber"
// extras pour un livre  : "isbn", "publisher", "pageCount"
// → mapping vers le modèle métier fait par un Mapper dédié à la collection
```

#### Contrat `MetadataProvider`

```kotlin
// domain/provider/MetadataProvider.kt
interface MetadataProvider {
    val id: String                                // "discogs", "musicbrainz", "tmdb"…
    val displayName: String                       // "Discogs"
    val supportedTypes: Set<CollectionType>       // un provider peut servir plusieurs types
    val capabilities: Set<ProviderCapability>     // ce qu'il sait faire

    fun isConfigured(): Boolean                   // a-t-il ce qu'il faut (token, etc.) ?

    suspend fun search(query: MetadataQuery): Result<List<MetadataResult>>
    suspend fun fetchDetails(externalId: String): Result<MetadataResult>
    suspend fun downloadCover(url: String): Result<ByteArray>
}

enum class ProviderCapability {
    SEARCH_BY_TEXT,
    SEARCH_BY_BARCODE,
    FETCH_DETAILS,
    DOWNLOAD_COVER
}
```

#### Registre + résolution Hilt

```kotlin
// domain/provider/ProviderRegistry.kt
interface ProviderRegistry {
    fun all(): List<MetadataProvider>
    fun forCollectionType(type: CollectionType): List<MetadataProvider>
    fun byId(id: String): MetadataProvider?
}

// di/ProviderModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Binds @IntoSet
    abstract fun bindDiscogs(impl: DiscogsProvider): MetadataProvider

    // Demain :
    // @Binds @IntoSet abstract fun bindMusicBrainz(impl: MusicBrainzProvider): MetadataProvider
    // @Binds @IntoSet abstract fun bindTmdb(impl: TmdbProvider): MetadataProvider
}
```

Les providers s'auto-enregistrent via `@IntoSet`. Le `ProviderRegistryImpl` reçoit `Set<MetadataProvider>` en injection et filtre par type/capability.

#### Usecase métier — toujours agnostique

```kotlin
// domain/usecase/EnrichRecordFromExternalSource.kt
class EnrichRecordFromExternalSource @Inject constructor(
    private val registry: ProviderRegistry
) {
    suspend operator fun invoke(
        type: CollectionType,
        query: MetadataQuery,
        preferredProviderId: String? = null
    ): Result<List<MetadataResult>> {
        val providers = registry.forCollectionType(type)
            .filter { it.isConfigured() }
            .let { if (preferredProviderId != null) it.sortedByDescending { p -> p.id == preferredProviderId } else it }

        if (providers.isEmpty()) return Result.success(emptyList())

        // Stratégie : premier provider qui retourne du résultat. À termes : merge / dédup.
        for (p in providers) {
            val res = p.search(query)
            if (res.isSuccess && res.getOrNull()?.isNotEmpty() == true) return res
        }
        return Result.success(emptyList())
    }
}
```

L'écran d'ajout ne connaît jamais "Discogs" — il appelle l'usecase avec `CollectionType.VINYL`. L'utilisateur peut, dans les réglages, **choisir le provider préféré** pour chaque type de collection.

#### Implémentation `DiscogsProvider`

```kotlin
// data/remote/providers/discogs/DiscogsProvider.kt
class DiscogsProvider @Inject constructor(
    private val api: DiscogsApi,
    private val authStore: DiscogsAuthStore,
    private val mapper: DiscogsMapper
) : MetadataProvider {

    override val id = "discogs"
    override val displayName = "Discogs"
    override val supportedTypes = setOf(CollectionType.VINYL)
    override val capabilities = setOf(
        ProviderCapability.SEARCH_BY_TEXT,
        ProviderCapability.SEARCH_BY_BARCODE,
        ProviderCapability.FETCH_DETAILS,
        ProviderCapability.DOWNLOAD_COVER
    )

    override fun isConfigured(): Boolean = authStore.hasToken()

    override suspend fun search(query: MetadataQuery): Result<List<MetadataResult>> = runCatching {
        when (query) {
            is MetadataQuery.ByBarcode -> api.search(barcode = query.code, token = authStore.token())
            is MetadataQuery.ByText    -> api.search(q = query.query, token = authStore.token())
            is MetadataQuery.ById      -> listOf(api.release(query.externalId.toLong(), authStore.token()))
        }.map(mapper::toMetadataResult)
    }

    // …
}
```

#### Stratégie d'authentification (point critique)

Trois options envisagées, dans l'ordre de préférence :

| Option | Avantages | Inconvénients | Quand |
|---|---|---|---|
| **A. Backend proxy** (recommandé à terme) | Token jamais sur le device · clés API protégées · throttling centralisé · cache partagé · possible analytics | Demande un backend (coût, ops) · perd le 100% offline pour l'enrichissement | Cible v2+ |
| **B. Token user Discogs en local** | Simple, conforme aux ToS Discogs · l'utilisateur utilise son propre quota | UX : l'utilisateur doit créer un compte Discogs et copier un Personal Access Token · stocké en `EncryptedSharedPreferences` | **MVP** |
| **C. Consumer Key/Secret embarqués + OAuth** | Pas de friction utilisateur sur le compte | Secrets embarqués = facilement extractibles de l'APK · violation des ToS · risque de révocation de la clé app | À éviter |

**Décision MVP : option B.** Le `DiscogsAuthStore` est une abstraction qui rendra trivial le basculement vers A plus tard.

```kotlin
// domain/provider/AuthStore.kt
interface AuthStore {
    fun isAuthenticated(): Boolean
    suspend fun token(): String?     // en option B : token user ; en option A : token de session backend
    suspend fun setToken(value: String)
    suspend fun clear()
}
```

L'écran **Réglages → Comptes & sources** liste tous les `MetadataProvider` enregistrés, indique `isConfigured()` (vert/gris), et propose les actions (saisir token, se connecter, se déconnecter). Côté code, ce sont les providers qui exposent leur **propre écran de configuration** via une petite abstraction `ProviderSetupScreen` (composable injecté), pour éviter de coder à la main chaque flow d'auth dans l'écran de réglages.

#### Migration future vers backend

Quand le backend sera prêt, on ajoute une nouvelle implémentation `BackendMetadataProvider` qui parle à ton API et qui, en interne, appelle Discogs côté serveur. **Côté app, rien d'autre ne change** : on bascule le `@Binds` dans `ProviderModule`, on retire le `DiscogsProvider` direct, l'usecase et les écrans ne bougent pas. L'`AuthStore` change de stratégie (token de session backend au lieu de token Discogs).

#### Politique réseau

- **Tout appel provider est explicite** : déclenché par l'utilisateur (scan, recherche manuelle, bouton "enrichir"). Aucun appel en arrière-plan, jamais.
- **Cache local** des résultats récents (Room table `metadata_cache`, TTL 30 jours) pour éviter de retaper la même requête à chaque ouverture.
- **Mode avion / offline** : `isConfigured()` continue de retourner `true` (l'app a son token), mais les appels retournent `Result.failure(NoNetworkException)` et l'UI affiche un état clair "Pas de connexion — saisie manuelle uniquement".
- **Throttling** : respect des rate limits Discogs (60 req/min authentifié) via un `RateLimitInterceptor` OkHttp.

#### Arborescence impactée

```
data/remote/
├── providers/
│   ├── discogs/
│   │   ├── DiscogsApi.kt
│   │   ├── DiscogsDto.kt
│   │   ├── DiscogsMapper.kt
│   │   ├── DiscogsAuthStore.kt
│   │   └── DiscogsProvider.kt
│   ├── cache/
│   │   └── MetadataCacheDao.kt
│   └── ProviderRegistryImpl.kt
domain/
├── provider/
│   ├── MetadataProvider.kt
│   ├── ProviderRegistry.kt
│   ├── ProviderCapability.kt
│   └── AuthStore.kt
└── usecase/
    └── EnrichRecordFromExternalSource.kt
```

#### Providers par type de collection (mapping cible)

Issu du rapport `rapport-providers-multi-collections.md`. Le MVP n'implémente que **Discogs (VINYL)** ; les autres providers sont listés ici comme cible pour assurer la cohérence d'architecture dès aujourd'hui.

| CollectionType | Provider principal | Format | Auth | Capabilities | Note |
|---|---|---|---|---|---|
| `VINYL` | Discogs | REST/JSON | Token user | text, barcode, details, cover | MVP |
| `LEGO_SET` | Rebrickable | REST/JSON | API key gratuite | text, id, details, cover, **bulk** | Dump CSV disponible (offline-friendly) |
| `BOARD_GAME` | BoardGameGeek (XMLAPI2) | REST/**XML** | App registration | text, details, cover | Retry HTTP 202 nécessaire, parsing XML (xmlutil) |
| `MANGA` | Google Books (primary) + AniList (enrich) | REST + GraphQL | Sans clé / Sans clé | text, barcode, details, cover | EnrichChain |
| `COMIC` | Google Books (+ Comic Vine pour US) | REST | Sans clé / API key | text, barcode, details, cover | Fallback Comic Vine |

#### `ProviderStrategy` par type

```kotlin
sealed interface ProviderStrategy {
    data class SingleProvider(val providerId: String) : ProviderStrategy
    data class Fallback(val providerIds: List<String>) : ProviderStrategy
    data class EnrichChain(
        val primary: String,
        val enrichers: List<String>,
        val mergePolicy: MergePolicy = MergePolicy.PRESERVE_PRIMARY
    ) : ProviderStrategy
}

enum class MergePolicy { PRESERVE_PRIMARY, PRESERVE_ENRICHER, COMBINE }

val defaultStrategies = mapOf(
    CollectionType.VINYL      to SingleProvider("discogs"),
    CollectionType.LEGO_SET   to SingleProvider("rebrickable"),
    CollectionType.BOARD_GAME to SingleProvider("bgg"),
    CollectionType.MANGA      to EnrichChain("googlebooks", listOf("anilist")),
    CollectionType.COMIC      to Fallback(listOf("googlebooks", "comicvine"))
)
```

L'usecase `EnrichRecordFromExternalSource` est étendu pour interpréter ces stratégies, et l'utilisateur peut surcharger la stratégie par type dans les réglages.

#### Capability-driven UI

L'écran d'ajout interroge la stratégie et les capabilities du `CollectionType` actif pour afficher / désactiver les options :
- Bouton "Scanner code-barres" → visible si au moins un provider du type déclare `SEARCH_BY_BARCODE` (cas VINYL, MANGA, COMIC ; absent pour BOARD_GAME, LEGO_SET).
- Bouton "Pré-charger la base offline" → visible si au moins un provider déclare `BULK_DOWNLOAD` (cas LEGO_SET).

Évite les boutons morts et adapte naturellement l'app à chaque collection.

#### Modèle `MetadataResult` — extras par type

Les champs communs (`title`, `primaryAuthor`, `year`, `coverUrl`, `genres`) sont en première classe. Les spécificités sont dans `extras: Map<String, String>`. Mapping cible :

- **VINYL** : `artist`, `label`, `format`, `country`, `catalogNumber`, `barcode`, `discogsReleaseId`.
- **LEGO_SET** : `setNumber`, `theme`, `subTheme`, `pieceCount`, `minifigCount`, `ageRange`, `originalPrice`, `originalCurrency`, `rebrickableId`.
- **BOARD_GAME** : `bggId`, `designers`, `publisher`, `minPlayers`, `maxPlayers`, `playingTimeMin`, `playingTimeMax`, `suggestedAge`, `mechanics`, `bggRank`, `bggRating`.
- **MANGA** : `series`, `seriesId`, `volume`, `isbn`, `publisherFr`, `originalAuthor`, `demographic`, `originalLang`, `translator`, `pageCount`, `googleBooksId`, `anilistMangaId`.
- **COMIC** : `series`, `seriesId`, `volume`, `volumeTitle`, `isbn`, `publisher`, `collection`, `authors`, `scenarist`, `drawer`, `colorist`, `pageCount`, `originalLang`, `format`.

#### `seriesId` — pivot pour la vue série

Présent dans `extras` pour MANGA et COMIC. Sert de clé pour :
- Regrouper les items d'une même série en vue SERIAL (cf. §6, "Collections supportées et modes de référencement").
- Lier les items à l'entité `SeriesEntity` (table dédiée).
- Permettre les fonctionnalités "afficher les manquants" / "alerte nouveau tome" en v2+.

---

## 7. Stockage des jaquettes

- Dossier : `context.filesDir/covers/{recordId}.jpg`.
- Format : JPEG qualité 85, redimensionnement max 1200px côté long.
- Service `CoverStorage` :
  ```kotlin
  interface CoverStorage {
      suspend fun saveFromUri(recordId: String, uri: Uri): String
      suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String
      suspend fun saveFromUrl(recordId: String, url: String): String
      suspend fun delete(recordId: String)
  }
  ```
- Coil charge les images via `File(coverPath)` directement.

---

## 8. Permissions

| Permission | Quand demandée | Justification UX |
|---|---|---|
| `CAMERA` | Au premier scan ou prise photo | "Pour scanner les codes-barres et photographier vos jaquettes" |
| `READ_MEDIA_IMAGES` (Android 13+) | Import galerie | "Pour importer des jaquettes existantes" |
| `INTERNET` | Manifest | Discogs (optionnel) |

Pas de permissions au lancement. Tout est demandé contextuellement.

---

## 9. Qualité, tests, performance

### Tests à fournir avec chaque feature
- **Domain** : test unitaire de chaque usecase (in-memory fake repo).
- **Data** : tests Room avec base in-memory.
- **UI** : tests Compose pour les écrans principaux (`createComposeRule()`).

### Performance
- LazyColumn avec `key = record.id` obligatoire.
- Pagination non nécessaire en dessous de ~10 000 disques (Room + Flow gèrent).
- Recherche : debounce 200ms sur le champ texte avant requête Room.
- Coil : `crossfade(true)`, `memoryCacheKey = recordId`.

### Accessibilité
- Tout `Image` a un `contentDescription` (titre album + artiste).
- Cibles tap ≥ 48dp.
- Tester avec TalkBack.
- Le grisage wishlist ne doit **pas** être la seule indication : ajouter l'icône cœur pour les daltoniens / lecteurs d'écran.

---

## 10. Conventions de code

- **Naming** : `RecordRow`, `RecordRowState`, `RecordRowPreview` côte à côte.
- **Compose** : un fichier = un écran ou un composant logique. Stateless préféré, state hoisté.
- **Previews** : chaque composant Compose a au moins une `@Preview` light + dark.
- **Strings** : tout dans `strings.xml`, FR par défaut, EN en `values-en/`.
- **Commits** : Conventional Commits (`feat:`, `fix:`, `refactor:`…).
- **Format** : ktlint + detekt en pre-commit.

---

## 11. Roadmap

> **État du projet (2026-05-18) : Sprint 6 en cours.** Le projet a démarré sous le nom **VinylCollection**, puis a été renommé **Acervus** au Sprint 4. La numérotation des sprints suit l'historique réel du dépôt et diffère donc de la roadmap cible initiale (où le rebranding et le sprint multi-artistes n'étaient pas anticipés).

### Sprints réalisés

#### Sprint 1 — Fondations [TERMINÉ]
- Setup projet (initialement `fr.beudaert.mvc`), thème Material You seedé, navigation Compose.
- Room + entité `Record` + DAO + repository.
- Écran liste vide + FAB.
- Écran ajout manuel basique.

#### Sprint 2 — Expérience liste [TERMINÉ]
- SearchBar Material3 flottante + filtres bottom sheet.
- Sticky headers alphabétiques.
- Affichage grisé wishlist.
- Écran détail complet.

#### Sprint 3 — Médias [TERMINÉ]
- `CoverStorage` + prise photo + galerie.
- Coil + placeholders.
- Édition complète d'un disque.

#### Sprint 4 — Refactoring général & rebranding "Acervus" [TERMINÉ]
- Renommage application : `VinylCollection` → **`Acervus`** (latin *acervus* = amas, collection — neutre vis-à-vis du type de média, cohérent avec l'abstraction `MetadataProvider` multi-collections).
- Renommage du package Kotlin : `fr.beudaert.mvc.*` → `fr.beudaert.acervus.*` (déplacement des sources `app/src/main/java/fr/beudaert/mvc/` → `…/acervus/`, idem `src/test/`).
- Mise à jour Gradle : `namespace = "fr.beudaert.acervus"`, `applicationId = "fr.beudaert.acervus"`.
- Renommage des classes typées "Vinyl" / "Mvc" :
  - `MvcApplication` → `AcervusApplication`
  - `VinylDatabase` → `AcervusDatabase` (nom de fichier SQLite : `vinyl_collection.db` → `acervus.db` — acceptable car pas encore de release Play Store)
  - `VinylTheme` / `VinylTypography` / `VinylShapes` → `AcervusTheme` / `AcervusTypography` / `AcervusShapes`
  - `VinylFormat` → `AcervusFormat`
  - `VinylNavGraph` / `VinylDestinations` → `AcervusNavGraph` / `AcervusDestinations`
- `AndroidManifest.xml` : `.MvcApplication` → `.AcervusApplication`, thème `@style/Theme.Mvc` → `@style/Theme.Acervus`.
- Ressources : `app_name` → `"Acervus"` dans `strings.xml` (FR + EN si présent), style `Theme.Mvc` → `Theme.Acervus` dans `themes.xml`.
- Mise à jour de tous les imports, des tests, et de la documentation (`README.md`, `CLAUDE.md`, ce fichier).
- Nettoyage du dossier `app/build/` après rename (code généré obsolète, Hilt + Room régénèrent au prochain build).

#### Sprint 5 — Multi-artistes & métadonnées étendues [TERMINÉ]
- Saisie de plusieurs auteurs/artistes par disque (duo, collaborations).
- Enregistrement des auteurs dans une table dédiée (`artists`) avec auto-complétion lors de la création/édition d'un album.
- Champ "type d'enregistrement" (vitesse de gravure) distinct du format physique : certains 33t sont gravés à 45 tr/min — il faut pouvoir le tracer (`RecordingSpeed { RPM_33, RPM_45, RPM_78, OTHER }`).
- Migration Room : table `artists`, table de jointure `record_artists` (n-n), nouvelle colonne `recording_speed` sur `records`.
- UI : champ artistes en `ChipGroup` éditable avec suggestions (DropdownMenu sur frappe), nouveau segmented button "Vitesse" dans le formulaire d'édition.
- **Affichage liste — albums multi-artistes** : toggle dans les Réglages `duplicate_albums_per_artist` (clé DataStore), **activé par défaut**.
  - **ON (défaut)** : un album à 3 artistes apparaît 3 fois dans la `LazyColumn` (sous chaque lettre du sticky header). Compteur global = `COUNT(DISTINCT record_id)` pour ne compter qu'une fois.
  - **OFF** : l'album n'apparaît que sous son artiste principal (premier de la liste `record_artists`, ordre préservé via colonne `position`). Les autres artistes sont affichés en sous-titre (`feat. …`).
  - Implémentation : le DAO expose deux requêtes (`observeFlattenedByArtist()` vs `observeByPrimaryArtist()`), le ViewModel choisit selon le flag du `SettingsRepository`. Clé Compose des lignes = `"$recordId#$artistId"` en mode ON pour éviter les conflits de recomposition.

### Sprint en cours

#### Sprint 6 — Sources externes (abstraction + scan code-barres) [EN COURS]
- Abstraction `MetadataProvider`, `ProviderRegistry`, `AuthStore` en place (cf. §6).
- Scan code-barres (ML Kit) — pleine page caméra + overlay viseur.
- **`DiscogsProvider`** comme première implémentation de l'abstraction.
- Au scan : déduplication sur `barcode` puis requête Discogs `database/search?barcode=…` si connexion, sinon formulaire pré-rempli.

> **Implémentation arrêtée à ce sprint à ce jour.** Les sprints suivants sont planifiés, non implémentés.

### Sprints planifiés

#### Sprint 7 — Polish vinyle, Import/Export & préparation Play Store [PLANIFIÉ]
- Animations transitions liste→détail (`SharedTransitionLayout`).
- Tests UI complets.
- Accessibilité (contentDescription, TalkBack).
- Import / Export CSV+JSON (préparation portabilité données utilisateur).
- Préparation Play Store (icônes, captures, fiche).

#### Sprint 8 — Activer une 2ᵉ collection (LEGO) [PLANIFIÉ]
- Écran de sélection de collection à la création.
- `RebrickableProvider` (REST/JSON, le plus simple à intégrer).
- Mapper `MetadataResult → CollectionItem` dédié à `LEGO_SET`.
- Réglages : ajout d'API key, gestion `AuthStore` provider.
- **Valide l'abstraction** sans introduire de complexité supplémentaire.
- À ce stade, refactor `Record → CollectionItem` (introduction de `collectionType`, `seriesId`, `volumeNumber` nullables).

#### Sprint 9 — Modes de référencement : vue série & 3 états [PLANIFIÉ]
- Table `SeriesEntity` + DAO.
- Toggle FLAT / SERIAL dans les filtres (visible selon `CollectionType`).
- Écran "Détail série" (grille des tomes).
- États OWNED / WISHLIST / **IGNORED** opérationnels en vue SERIAL.
- Migration Room : enum `RecordStatus → ItemStatus` (ajout IGNORED).
- Bascule d'état via tap long sur tile.

#### Sprint 10 — Providers chaînés (manga) [PLANIFIÉ]
- `GoogleBooksProvider` (REST/JSON, sans clé).
- `AniListProvider` (GraphQL via Apollo Kotlin).
- `ProviderStrategy.EnrichChain` implémenté dans `EnrichRecordFromExternalSource`.
- Test bout-en-bout : scan ISBN d'un tome → Google Books → AniList enrich → `CollectionItem` complet avec `seriesId`.

#### Sprint 11 — BoardGameGeek [PLANIFIÉ]
- Démarche d'enregistrement application BGG (à lancer **en amont** — vu le délai de réponse, idéalement dès le début du sprint 8).
- `BggProvider` (XMLAPI2, parsing `xmlutil`).
- `RateLimitInterceptor` configurable par provider.
- Retry HTTP 202 avec backoff exponentiel.
- Cache local agressif (TTL 90j).

#### Sprint 12 — BD [PLANIFIÉ]
- Activation `COMIC` sur `GoogleBooksProvider` (réutilisation).
- `ComicVineProvider` en fallback pour comics US.
- Vue SERIAL pour les longues séries BD (Tintin, Astérix, XIII, Largo Winch…).

#### Sprint 13+ — Backend & au-delà [PLANIFIÉ]
- Évaluation backend Acervus pour proxifier les providers (gestion centralisée des clés, throttling, cache partagé).
- Bascule progressive : `BackendMetadataProvider` remplaçant ou enveloppant les providers directs.
- Synchronisation multi-appareils (post-MVP, post-backend).
- Vue transverse "créateurs" (auteurs, artistes, mangakas) cross-collection.

---

## 12. Instructions pour l'assistant IA

Quand on te demande d'implémenter ou de modifier une partie de cette app :

1. **Respecte l'architecture** décrite en §3. Toute couche violée doit être justifiée explicitement.
2. **Génère du code complet et compilable**, jamais d'extraits avec `// TODO` sauf demande explicite.
3. **Inclue toujours** : imports complets, previews Compose pour les UI, tests pour les usecases.
4. **Ne réintroduis pas** de dépendance réseau pour des features marquées offline.
5. **Préfère les Flow** aux suspend qui renvoient des listes pour tout ce qui s'affiche.
6. Si une demande est ambiguë (ex : "ajoute le tri"), pose **une** question ciblée avant de coder.
7. Pour toute nouvelle entité ou colonne Room, écris la **migration** correspondante.
8. Avant de proposer une lib externe non listée ici, justifie pourquoi celles déjà en place ne suffisent pas.

---

*Fin du contexte. Version 1.1 — fusion vision cible Acervus + historique réel du projet (Sprint 6 en cours).*