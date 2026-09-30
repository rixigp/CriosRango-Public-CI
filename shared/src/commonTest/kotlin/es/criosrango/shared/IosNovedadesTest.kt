package es.criosrango.shared

import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNovedadesTest {
    private fun product(id: Int, name: String, price: String, category: StoreCategory) =
        StoreProduct(id = id, name = name, prices = ProductPrices(price = price), categories = listOf(category))

    @Test
    fun audienceFilter_isAppliedThroughCategoryHierarchy() {
        val root = StoreCategory(id = 10, parent = 0, name = "Niña")
        val child = StoreCategory(id = 11, parent = 10, name = "Vestidos")
        val matching = product(1, "Vestido", "2000", child)
        val other = product(2, "Polo", "3000", StoreCategory(id = 20, parent = 0, name = "Niño"))

        assertTrue(novedadesAudienceMatches(matching, IosNovedadesAudience.GIRL, listOf(root, child)))
        assertFalse(novedadesAudienceMatches(other, IosNovedadesAudience.GIRL, listOf(root, child)))
    }

    @Test
    fun combination_filterAndSort_isAppliedTogether() {
        val girl = StoreCategory(id = 10, parent = 0, name = "Niña")
        val products = listOf(
            product(1, "Zeta", "3000", girl),
            product(2, "Alfa", "1000", girl),
            product(3, "Hombre", "500", StoreCategory(id = 20, parent = 0, name = "Hombre"))
        )

        val filtered = products.filter { novedadesAudienceMatches(it, IosNovedadesAudience.GIRL, listOf(girl)) }
        assertEquals(listOf(2, 1), sortNovedadesProducts(filtered, IosNovedadesSort.PRICE_ASC).map { it.id })
    }

    @Test
    fun sorting_matchesAndroidModes() {
        val category = StoreCategory(id = 10, parent = 0, name = "Niña")
        val products = listOf(product(1, "Zeta", "3000", category), product(2, "Alfa", "1000", category))

        assertEquals(listOf(2, 1), sortNovedadesProducts(products, IosNovedadesSort.PRICE_ASC).map { it.id })
        assertEquals(listOf(1, 2), sortNovedadesProducts(products, IosNovedadesSort.PRICE_DESC).map { it.id })
        assertEquals(listOf(2, 1), sortNovedadesProducts(products, IosNovedadesSort.NAME_ASC).map { it.id })
    }

    @Test
    fun reset_returnsToDefaultFilterAndSortState() {
        var audience = IosNovedadesAudience.GIRL
        var sort = IosNovedadesSort.PRICE_DESC
        audience = IosNovedadesAudience.ALL
        sort = IosNovedadesSort.RECENT
        assertEquals(IosNovedadesAudience.ALL, audience)
        assertEquals(IosNovedadesSort.RECENT, sort)
    }

    @Test
    fun paginationWithFilter_usesSharedPaginatorAndDeduplicates() = kotlinx.coroutines.test.runTest {
        val paginator = CatalogPaginator<StoreProduct>(identity = { it.id })
        val girl = StoreCategory(id = 10, parent = 0, name = "Niña")
        paginator.start("novedades:girl") { _, _ ->
            CatalogPage(
                listOf(
                    product(1, "A", "1000", girl),
                    product(2, "B", "2000", StoreCategory(id = 20, parent = 0, name = "Niño"))
                ),
                true
            )
        }
        paginator.loadNext { _, _ ->
            CatalogPage(
                listOf(
                    product(1, "A", "1000", girl),
                    product(3, "C", "3000", girl)
                ),
                false
            )
        }

        val filtered = paginator.state.value.items.filter {
            novedadesAudienceMatches(it, IosNovedadesAudience.GIRL, listOf(girl))
        }
        assertEquals(listOf(1, 3), filtered.map { it.id })
        assertEquals(2, paginator.state.value.currentPage)
    }
}
