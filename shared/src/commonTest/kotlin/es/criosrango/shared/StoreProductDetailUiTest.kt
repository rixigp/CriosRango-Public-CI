package es.criosrango.shared

import es.criosrango.shared.model.StoreBrandImage
import es.criosrango.shared.model.StoreGlobalBrand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreProductDetailUiTest {
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
    fun resolvesBrandLogoFromTheExistingBrandCatalogue() {
        val brand = StoreGlobalBrand(
            id = 7,
            name = "Spagnolo",
            slug = "spagnolo",
            count = 4,
            image = StoreBrandImage(src = "https://cdn.example.test/spagnolo.png")
        )
        val result = resolveStoreProductBrand(
            "Pantalón – 154385126E88 – Spagnolo",
            "Marca: Spagnolo",
            listOf(brand)
        )
        assertEquals("Spagnolo", result?.name)
        assertEquals("https://cdn.example.test/spagnolo.png", result?.logoUrl)
    }

    @Test
    fun brandNameRemainsAvailableWhenNoLogoExists() {
        val brand = StoreGlobalBrand(id = 7, name = "Spagnolo", slug = "spagnolo", count = 4)
        val result = resolveStoreProductBrand("Pantalón – Spagnolo", "Marca: Spagnolo", listOf(brand))
        assertEquals("Spagnolo", result?.name)
        assertNull(result?.logoUrl)
    }

    @Test
    fun emptyCompositionIsOmitted() {
        assertNull(storeProductCompositionText(null))
        assertNull(storeProductCompositionText("Composición: "))
        assertEquals("Composición: 100% algodón", storeProductCompositionText("Composición: 100% algodón"))
    }
}
