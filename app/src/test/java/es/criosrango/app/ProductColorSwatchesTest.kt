package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProductColorSwatchesTest {
    @Test fun crossesOnlyDeclaredColorsBackedByVariationsInDeclaredOrder() {
        val product = StoreProduct(
            attributes = listOf(ProductAttribute("Color", terms = listOf(AttributeTerm(name = "Celeste"), AttributeTerm(name = "Rosa")))),
            variations = listOf(ProductVariation(id = 1, attributes = listOf(VariationAttribute("Color", "celeste"), VariationAttribute("Tallas", "12M"))))
        )
        assertEquals(listOf("Celeste"), product.crossedProductColors())
    }

    @Test fun deduplicatesColorCrossesByCatalogFilterKey() {
        val product = StoreProduct(
            attributes = listOf(ProductAttribute("Color", terms = listOf(AttributeTerm(name = "Blanco"), AttributeTerm(name = " blanco "), AttributeTerm(name = "Rosa")))),
            variations = listOf(ProductVariation(attributes = listOf(VariationAttribute("Color", "BLANCO"))), ProductVariation(attributes = listOf(VariationAttribute("Color", "rosa"))))
        )
        assertEquals(listOf("Blanco", "Rosa"), product.crossedProductColors())
    }

    @Test fun ignoresDeclaredColorsWithoutVariationCross() {
        val product = StoreProduct(
            attributes = listOf(ProductAttribute("Color", terms = listOf(AttributeTerm(name = "Oro")))),
            variations = listOf(ProductVariation(attributes = listOf(VariationAttribute("Color", "Plata"))))
        )
        assertEquals(emptyList(), product.crossedProductColors())
    }

    @Test fun realCatalogMappingsAreRepresentableAndHexRemainsSupported() {
        listOf("Berenjena", "Caldero", "Chicle", "Coral", "Kaki", "Oro", "Pistacho", "salmón").forEach { assertNotNull(productColorSwatch(it)) }
        assertNotNull(productColorSwatch("#FFFFFF"))
    }
}