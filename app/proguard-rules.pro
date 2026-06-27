# Règles ProGuard / R8 spécifiques à Acervatim.
# Activé en release (isMinifyEnabled = true + isShrinkResources = true).
# Room, Hilt et Coil 3 embarquent leurs propres règles (consumer rules) : rien à ajouter.
# Les sections ci-dessous couvrent ce que R8 ne préserve pas tout seul.

# --- kotlinx.serialization -----------------------------------------------------
# R8 retire les classes/serializers générés si on ne les garde pas explicitement.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Garder les serializers générés (companion `serializer()` + objets $serializer).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Garder les classes @Serializable du projet (DTO réseau + modèles backup).
-keep,includedescriptorclasses class app.acervatim.**$$serializer { *; }
-keepclassmembers class app.acervatim.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class app.acervatim.** { *; }

# --- Retrofit + OkHttp ---------------------------------------------------------
# Règles officielles : garder les signatures génériques et les annotations des
# interfaces d'API, sinon Retrofit ne sait plus construire les appels.
-keepattributes Signature, Exceptions
-keepclasseswithmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- ML Kit Barcode (modèle bundlé) -------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**
