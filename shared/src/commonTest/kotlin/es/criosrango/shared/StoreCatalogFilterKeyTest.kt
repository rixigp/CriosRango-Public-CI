package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCatalogFilterKeyTest {
    @Test
    fun removesCanonicalAccentsAndNormalizesWhitespace() {
        assertEquals("arbol", storeCatalogFilterKey("Árbol"))
        assertEquals("arbol", storeCatalogFilterKey("árbol"))
        assertEquals("cafe nino", storeCatalogFilterKey("  CAFÉ\tNiÑO  "))
        assertEquals("cafe nino", storeCatalogFilterKey("  CAFE   NINO  "))
    }

    @Test
    fun foldsPrecomposedAndDecomposedFormsEqually() {
        assertEquals("e", storeCatalogFilterKey("é"))
        assertEquals("e", storeCatalogFilterKey("e\u0301"))
        assertEquals(
            storeCatalogFilterKey("é"),
            storeCatalogFilterKey("e\u0301")
        )
        assertEquals("angstrom", storeCatalogFilterKey("Ångström"))
        assertEquals("angstrom", storeCatalogFilterKey("A\u030Angstro\u0308m"))
    }

    @Test
    fun preservesSharpSWithoutExpandingItToSs() {
        assertEquals("straße", storeCatalogFilterKey("STRAẞE"))
        assertEquals("straße", storeCatalogFilterKey("Straße"))
        assertEquals("straße", storeCatalogFilterKey("straße"))
        assertEquals("strasse", storeCatalogFilterKey("strasse"))
    }
}
