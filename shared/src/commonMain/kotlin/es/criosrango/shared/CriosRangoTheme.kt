package es.criosrango.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Brand color scheme shared by Android and iOS.
 *
 * Keep the approved Android primary/secondary and existing neutral surfaces.
 * Explicit container/tint roles prevent Material 3's purple defaults leaking into
 * controls that consume MaterialTheme.colorScheme implicitly.
 */
val CriosRangoLightColorScheme = lightColorScheme(
    primary = Color(0xFF183B35),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EFEB),
    onPrimaryContainer = Color(0xFF183B35),
    secondary = Color(0xFFD18162),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCFAF7),
    onSecondaryContainer = Color(0xFF183B35),
    tertiary = Color(0xFFB9D8CE),
    onTertiary = Color(0xFF183B35),
    tertiaryContainer = Color(0xFFE3EFEB),
    onTertiaryContainer = Color(0xFF183B35),
    background = Color(0xFFFCFAF7),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFCFAF7),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFFCFAF7),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAF7),
    surfaceContainer = Color(0xFFFCFAF7),
    surfaceContainerHigh = Color(0xFFF4F2EF),
    surfaceContainerHighest = Color(0xFFDADBD6),
    surfaceTint = Color(0xFF183B35),
    inverseSurface = Color(0xFF31312F),
    inverseOnSurface = Color(0xFFF4F2EF),
    inversePrimary = Color(0xFFB9D8CE),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFFDADBD6)
)

@Composable
fun CriosRangoTheme(content: @Composable () -> Unit) {
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = 1f
        )
    ) {
        MaterialTheme(
            colorScheme = CriosRangoLightColorScheme,
            content = content
        )
    }
}
