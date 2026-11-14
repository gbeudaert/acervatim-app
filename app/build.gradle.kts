import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Signature release — lue depuis keystore.properties (racine, non versionné).
// Absent en CI/poste sans keystore : on retombe sur une release non signée plutôt
// que de casser le build (utile pour `assembleDebug` / tests).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

// URL du backend visée par la variante debug. Par défaut l'API publique (le debug reste
// utilisable tel quel) ; pour taper un backend local, poser dans `local.properties` (non
// versionné) :  devApiBaseUrl=http://192.168.1.20:3000/   (émulateur : http://10.0.2.2:3000/)
val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) FileInputStream(localPropsFile).use { load(it) }
}
val devApiBaseUrl: String = localProps.getProperty("devApiBaseUrl")?.takeIf { it.isNotBlank() }
    ?: "https://api.acervatim.app/"

android {
    namespace = "app.acervatim"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.acervatim"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "0.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // applicationId distinct : la build locale s'installe *à côté* de celle du Play
            // Store (nom « Acervatim Dev » et fond d'icône orange via src/debug/res).
            // Contrepartie : le client OAuth Android doit exister pour ce package
            // (cf. docs developpeur/auth-google.md), sinon Sign-In → DEVELOPER_ERROR 10.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Backend de développement, surchargeable par `devApiBaseUrl` dans local.properties.
            buildConfigField("String", "API_BASE_URL", "\"$devApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Ne signe avec la clé release que si le keystore est présent ; sinon bundle
            // non signé (à signer manuellement). Évite un échec de build sans keystore.
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            buildConfigField("String", "API_BASE_URL", "\"https://api.acervatim.app/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * JVM des tests unitaires épinglée sur le JDK 21, indépendamment de celle du daemon Gradle.
 *
 * Robolectric réécrit le bytecode avec ASM ; celui embarqué dans la 4.13 s'arrête à Java 22
 * (`Unsupported class file major version 70` dès qu'il croise une classe du JDK 26). L'échec ne
 * tombe pas sur l'assertion mais au *teardown* — « Some resetters failed » —, ce qui le fait passer
 * pour une régression du code alors que c'est la JVM d'exécution. Le daemon reste libre de tourner
 * sous un JDK récent : seul le fork de test redescend.
 *
 * À retirer le jour où Robolectric saura instrumenter sous le JDK du daemon.
 */
val unitTestLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(unitTestLauncher)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Material library (provides material-color-utilities for seeded theme)
    implementation(libs.google.material)

    // Coil 3 (image loading)
    implementation(libs.coil.compose)

    // EXIF (orientation correction when importing photos)
    implementation(libs.androidx.exifinterface)

    // CameraX + ML Kit Barcode (offline bundled model)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)

    // DataStore Preferences (user settings)
    implementation(libs.androidx.datastore.preferences)

    // JSON serialization (backup export / import + DTO réseau)
    implementation(libs.kotlinx.serialization.json)

    // Réseau — backend Acervatim (Retrofit + OkHttp, converter kotlinx.serialization)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Auth — Google Sign-In (Credential Manager) + stockage chiffré du JWT
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.security.crypto)

    // Synchronisation planifiée en arrière-plan
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Core library desugaring (java.time.* on API 24+)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose tooling (debug only)
    debugImplementation(libs.androidx.ui.tooling)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    // Tests UI Compose exécutés sur la JVM via Robolectric (pas de device requis).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    // ui-test-manifest doit être en debug : son manifest (déclarant la ComponentActivity utilisée
    // par createComposeRule) doit fusionner dans le manifest debug que Robolectric charge.
    debugImplementation(libs.androidx.ui.test.manifest)
}
