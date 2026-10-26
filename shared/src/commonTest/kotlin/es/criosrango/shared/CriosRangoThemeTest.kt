package es.criosrango.shared

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class CriosRangoThemeTest {
    @Test
    fun brandPrimaryAndSecondaryMatchApprovedColors() {
        assertEquals(Color(0xFF183B35), CriosRangoLightColorScheme.primary)
        assertEquals(Color(0xFFD18162), CriosRangoLightColorScheme.secondary)
    }

    @Test
    fun materialAccentAndContainerRolesAvoidPurpleDefaults() {
        val scheme = CriosRangoLightColorScheme
        assertEquals(Color(0xFF183B35), scheme.onPrimaryContainer)
        assertEquals(Color(0xFFFCFAF7), scheme.secondaryContainer)
        assertEquals(Color(0xFF183B35), scheme.onSecondaryContainer)
        assertEquals(Color(0xFFB9D8CE), scheme.tertiary)
        assertEquals(Color(0xFF183B35), scheme.onTertiary)
        assertEquals(Color(0xFFE3EFEB), scheme.primaryContainer)
        assertEquals(Color(0xFFE3EFEB), scheme.tertiaryContainer)
        assertEquals(Color(0xFF183B35), scheme.surfaceTint)
        assertEquals(Color(0xFFB9D8CE), scheme.inversePrimary)
    }
}
