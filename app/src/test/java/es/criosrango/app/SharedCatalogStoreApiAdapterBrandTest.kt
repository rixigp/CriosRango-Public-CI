package es.criosrango.app

import es.criosrango.shared.model.ProductBrand as SharedProductBrand
import es.criosrango.shared.model.StoreProduct as SharedStoreProduct
import es.criosrango.shared.resolveStoreProductBrand
import es.criosrango.shared.storeProductDisplayTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCatalogStoreApiAdapterBrandTest {
    @Test
    fun sharedTaxonomyBrandSurvivesAndroidConversionAndFeedsTitleAndFallback() {
        val sharedProduct = SharedStoreProduct(
            id = 102539846,
            name = "Camisa caballero vestir – 102539846M23 – Spagnolo",
            brands = listOf(SharedProductBrand(id = 7, name = "Spagnolo", slug = "spagnolo"))
        )

        val androidProduct = sharedProduct.toAndroid()
        val resolvedBrand = resolveStoreProductBrand(
            androidProduct.brands.map {
                SharedProductBrand(id = it.id, name = it.name, slug = it.slug)
            },
            emptyList()
        )

        assertEquals("Spagnolo", resolvedBrand?.name)
        assertEquals(
            "Camisa caballero vestir – 102539846M23",
            storeProductDisplayTitle(androidProduct.name, resolvedBrand?.name)
        )
        assertEquals("Spagnolo", resolvedBrand?.name)
        assertEquals(null, resolvedBrand?.logoUrl)
    }

    @Test
    fun androidTaxonomyBrandSurvivesSharedOutletConversion() {
        val androidProduct = StoreProduct(
            id = 102539846,
            brands = listOf(ProductBrandTerm(id = 7, name = "Spagnolo", slug = "spagnolo"))
        )

        val sharedProduct = androidProduct.toSharedOutletProduct()

        assertEquals(
            listOf(SharedProductBrand(id = 7, name = "Spagnolo", slug = "spagnolo")),
            sharedProduct.brands
        )
    }
}
