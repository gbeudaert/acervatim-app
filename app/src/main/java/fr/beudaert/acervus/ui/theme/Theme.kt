package fr.beudaert.acervus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity

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
    val scheme: DynamicScheme = if (expressive) {
        SchemeExpressive(hct, dark, contrastLevel)
    } else {
        SchemeFidelity(hct, dark, contrastLevel)
    }
    return scheme.toComposeColorScheme()
}
