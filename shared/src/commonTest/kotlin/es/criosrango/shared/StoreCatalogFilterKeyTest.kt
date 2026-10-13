package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCatalogFilterKeyTest {
    @Test
    fun foldsCanonicalAccentsAndNormalizesWhitespace() {
        assertEquals("cafe nino", storeCatalogFilterKey("  CAFÉ\tNiÑO  "))
    }

    @Test
    fun foldsPrecomposedAndDecomposedFormsEqually() {
        assertEquals("angstrom", storeCatalogFilterKey("Ångström"))
        assertEquals("angstrom", storeCatalogFilterKey("A\u030Angstro\u0308m"))
    }

    @Test
    fun preservesNonDiacriticLetters() {
        assertEquals("straße", storeCatalogFilterKey("STRAẞE"))
    }
}
