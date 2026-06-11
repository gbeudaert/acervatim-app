package fr.beudaert.acervatim.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.MaterialDynamicColors

/**
 * Maps a [DynamicScheme] (Material color utilities) into a Compose Material3 [ColorScheme].
 * Each Material role is resolved by [MaterialDynamicColors] for the given scheme.
 */
internal fun DynamicScheme.toComposeColorScheme(): ColorScheme {
    val m = MaterialDynamicColors()
    return ColorScheme(
        primary = Color(m.primary().getArgb(this)),
        onPrimary = Color(m.onPrimary().getArgb(this)),
        primaryContainer = Color(m.primaryContainer().getArgb(this)),
        onPrimaryContainer = Color(m.onPrimaryContainer().getArgb(this)),
        inversePrimary = Color(m.inversePrimary().getArgb(this)),
        secondary = Color(m.secondary().getArgb(this)),
        onSecondary = Color(m.onSecondary().getArgb(this)),
        secondaryContainer = Color(m.secondaryContainer().getArgb(this)),
        onSecondaryContainer = Color(m.onSecondaryContainer().getArgb(this)),
        tertiary = Color(m.tertiary().getArgb(this)),
        onTertiary = Color(m.onTertiary().getArgb(this)),
        tertiaryContainer = Color(m.tertiaryContainer().getArgb(this)),
        onTertiaryContainer = Color(m.onTertiaryContainer().getArgb(this)),
        background = Color(m.background().getArgb(this)),
        onBackground = Color(m.onBackground().getArgb(this)),
        surface = Color(m.surface().getArgb(this)),
        onSurface = Color(m.onSurface().getArgb(this)),
        surfaceVariant = Color(m.surfaceVariant().getArgb(this)),
        onSurfaceVariant = Color(m.onSurfaceVariant().getArgb(this)),
        surfaceTint = Color(m.surfaceTint().getArgb(this)),
        inverseSurface = Color(m.inverseSurface().getArgb(this)),
        inverseOnSurface = Color(m.inverseOnSurface().getArgb(this)),
        error = Color(m.error().getArgb(this)),
        onError = Color(m.onError().getArgb(this)),
        errorContainer = Color(m.errorContainer().getArgb(this)),
        onErrorContainer = Color(m.onErrorContainer().getArgb(this)),
        outline = Color(m.outline().getArgb(this)),
        outlineVariant = Color(m.outlineVariant().getArgb(this)),
        scrim = Color(m.scrim().getArgb(this)),
        surfaceBright = Color(m.surfaceBright().getArgb(this)),
        surfaceDim = Color(m.surfaceDim().getArgb(this)),
        surfaceContainer = Color(m.surfaceContainer().getArgb(this)),
        surfaceContainerHigh = Color(m.surfaceContainerHigh().getArgb(this)),
        surfaceContainerHighest = Color(m.surfaceContainerHighest().getArgb(this)),
        surfaceContainerLow = Color(m.surfaceContainerLow().getArgb(this)),
        surfaceContainerLowest = Color(m.surfaceContainerLowest().getArgb(this))
    )
}
