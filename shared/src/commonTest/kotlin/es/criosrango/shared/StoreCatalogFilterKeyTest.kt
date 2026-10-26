package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreCatalogFilterKeyTest {
    @Test
    fun novedadesOnlyShowsEnabledOrSelectedOptionsForSizesColorsAndBrands() {
        val allOptions = listOf("2M", "3M", "6M", "M", "L", "XL", "XXL", "1M", "4M")
        val enabled = setOf("2M", "3M", "6M", "M", "L", "XL", "XXL")
        val selectedButNowDisabled = setOf("4M")
        val expected = listOf("2M", "3M", "6M", "M", "L", "XL", "XXL", "4M")
        listOf("Talla", "Color", "Marca").forEach { dimension ->
            assertEquals(
                expected,
                visibleStoreCatalogFilterOptions(allOptions, enabled, selectedButNowDisabled, hideUnavailableOptions = true),
                "$dimension must retain selected options even when unavailable"
            )
        }
        assertEquals(
            allOptions,
            visibleStoreCatalogFilterOptions(allOptions, enabled, selectedButNowDisabled, hideUnavailableOptions = false)
        )
    }

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

    @Test
    fun decodes_html_entities_for_display_labels_only() {
        assertEquals("Abel & Lula", normalizeFilterMetadataDisplayName("Abel &amp; Lula"))
        assertEquals("Tachi & Zucca", normalizeFilterMetadataDisplayName("Tachi &amp; Zucca"))
        assertEquals("V&L", normalizeFilterMetadataDisplayName("V&amp;L"))
        assertEquals("X&M", normalizeFilterMetadataDisplayName("X&amp;M"))
        assertEquals("Café ©", normalizeFilterMetadataDisplayName("Caf&eacute; &copy;"))
        assertEquals("A😀B", normalizeFilterMetadataDisplayName("A&#x1F600;B"))
        assertEquals("&unknown;", normalizeFilterMetadataDisplayName("&unknown;"))
    }

    @Test
    fun display_decoding_does_not_change_canonical_ids_taxonomy_or_slugs() {
        val original = StoreFilterOption(id = 12, name = "V&amp;L", slug = "v-l", taxonomy = "product_tag", count = 7)
        val display = original.copy(name = normalizeFilterMetadataDisplayName(original.name))
        assertEquals("V&L", display.name)
        assertEquals(original.id, display.id)
        assertEquals(original.slug, display.slug)
        assertEquals(original.taxonomy, display.taxonomy)
        assertEquals(original.count, display.count)
    }

    @Test
    fun exact_decoded_duplicate_labels_collapse_without_case_folding() {
        assertEquals(
            listOf("42", "V&L", "v&l", "Azul", "Azul claro", "Azul marino"),
            distinctFilterMetadataDisplayNames(listOf(
                "42", "42", "V&amp;L", "V&L", "v&l", "Azul", "Azul claro", "Azul marino"
            ).map(::normalizeFilterMetadataDisplayName))
        )
    }

    @Test
    fun brand_search_is_local_case_insensitive_and_accent_tolerant() {
        val brands = listOf("Amaya", "Ámbar", "Niño", normalizeFilterMetadataDisplayName("Abel &amp; Lula"), "Amaya")
        assertEquals(listOf("Amaya", "Ámbar"), filterBrandDisplayNames(brands, "AM"))
        assertEquals(listOf("Niño"), filterBrandDisplayNames(brands, "nino"))
        assertEquals(listOf("Abel & Lula"), filterBrandDisplayNames(brands, "abel & lula"))
        assertTrue(filterBrandDisplayNames(brands, "not found").isEmpty())
    }
}
