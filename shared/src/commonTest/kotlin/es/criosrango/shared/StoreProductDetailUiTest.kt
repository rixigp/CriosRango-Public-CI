package es.criosrango.shared

import es.criosrango.shared.model.ProductBrand
import es.criosrango.shared.model.StoreBrandImage
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreProductDetailUiTest {
    @Test
    fun productDetailUsesOnlyTheLongDescriptionAndNeverTheShortDescription() {
        val product = StoreProduct(
            shortDescription = "Composición: 100% algodón\nMarca: marca inventada\nPregunta por más tallas...",
            description = "Descripción larga real del producto."
        )

        assertEquals("Descripción larga real del producto.", storeProductLongDescription(product))
    }

    @Test
    fun longDescriptionIsAvailableOnlyWhenAccordionIsExpanded() {
        val description = "Descripción larga real del producto."
        val initial = StoreProductDescriptionAccordionState()

        assertFalse(initial.isExpanded)
        assertNull(visibleStoreProductDescription(description, initial))

        val opened = toggleStoreProductDescription(initial)
        assertTrue(opened.isExpanded)
        assertEquals(description, visibleStoreProductDescription(description, opened))

        val closed = toggleStoreProductDescription(opened)
        assertFalse(closed.isExpanded)
        assertNull(visibleStoreProductDescription(description, closed))
    }

    @Test
    fun emptyLongDescriptionDoesNotRenderAnAccordion() {
        assertFalse(shouldRenderStoreProductDescription(""))
        assertFalse(shouldRenderStoreProductDescription("  \n  "))
        assertFalse(shouldRenderStoreProductDescription("<p>  </p>"))
        assertTrue(shouldRenderStoreProductDescription("Texto de descripción"))
    }

    @Test
    fun stripsOnlyAnIndependentTrailingBrandSuffix() {
        assertEquals(
            "Pantalón – 154385126E88",
            storeProductDisplayTitle("Pantalón – 154385126E88 – Spagnolo", "Spagnolo")
        )
        assertEquals(
            "Spagnolo para niños",
            storeProductDisplayTitle("Spagnolo para niños", "Spagnolo")
        )
    }

    @Test
    fun resolvesBrandLogoFromTheProductBrandTaxonomy() {
        val taxonomyBrand = ProductBrand(id = 7, name = "Spagnolo", slug = "spagnolo")
        val catalogueBrand = StoreGlobalBrand(
            id = 7,
            name = "Spagnolo",
            slug = "spagnolo",
            count = 4,
            image = StoreBrandImage(src = "https://cdn.example.test/spagnolo.png")
        )
        val result = resolveStoreProductBrand(listOf(taxonomyBrand), listOf(catalogueBrand))

        assertEquals("Spagnolo", result?.name)
        assertEquals("https://cdn.example.test/spagnolo.png", result?.logoUrl)
    }

    @Test
    fun brandNameFallsBackToTaxonomyTextWhenNoLogoExists() {
        val taxonomyBrand = ProductBrand(id = 7, name = "Spagnolo", slug = "spagnolo")
        val result = resolveStoreProductBrand(listOf(taxonomyBrand), emptyList())

        assertEquals("Spagnolo", result?.name)
        assertNull(result?.logoUrl)
    }
}
