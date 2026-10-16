package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreBrandsControllerTest {
    private fun httpClient(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun productBrandsParsesImageAndSendsHideEmpty() = runTest {
        var hideEmpty: String? = null
        var perPage: String? = null
        var page: String? = null
        val client = httpClient { request ->
            hideEmpty = request.url.parameters["hide_empty"]
            perPage = request.url.parameters["per_page"]
            page = request.url.parameters["page"]
            respond("""[{"id":7,"name":"Marca","slug":"marca","count":4,"image":{"src":"","thumbnail":"https://img.test/logo.png"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val brands = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client).productBrands(perPage = 100, page = 2, hideEmpty = true)
            assertEquals("true", hideEmpty)
            assertEquals("100", perPage)
            assertEquals("2", page)
            assertEquals(7, brands.single().id)
            assertEquals("Marca", brands.single().name)
            assertEquals("marca", brands.single().slug)
            assertEquals(4, brands.single().count)
            assertEquals("https://img.test/logo.png", brands.single().imageUrl)
        } finally { client.close() }
    }

    @Test
    fun brandProductQueryDoesNotSerializeTag() = runTest {
        var params: io.ktor.http.Parameters? = null
        val client = httpClient { request ->
            params = request.url.parameters
            respond("""[{"id":1,"name":"Producto"}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            StoreApiClient("https://example.test/wp-json/wc/store/v1/", client).products(brand = "x", page = 1, perPage = 12)
            assertEquals("x", params!!["brand"])
            assertNull(params!!["tag"])
            assertTrue(params!!.names().none { it.startsWith("tag") })
        } finally { client.close() }
    }

    @Test
    fun controllerLoadsAllBrandPagesAndPaginatesSelectedBrand() = runTest {
        try {
        val requests = mutableListOf<String>()
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
            requests += request.url.toString()
            when {
                request.url.encodedPath.endsWith("/products/brands") && request.url.parameters["page"] == "1" ->
                    respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products/brands") ->
                    respond("""[{"id":1,"name":"Duplicate","slug":"duplicate","count":9},{"id":101,"name":"Alpha","slug":"alpha","count":2},{"id":102,"name":"Empty","slug":"empty","count":0}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") && request.url.parameters["page"] == "1" ->
                    respond((1..12).joinToString(prefix="[", postfix="]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") ->
                    respond("""[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        try {
            val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            val controller = StoreBrandsController(api, this)
            advanceUntilIdle()
            val brands = controller.state.value.brands
            assertEquals(101, brands.size, "ASSERT_ALL_BRAND_PAGES_SIZE expected=101 actual=${brands.size} requests=$requests")
            assertEquals("Alpha", brands.first().name, "ASSERT_SORTED_FIRST_BRAND expected=Alpha actual=${brands.firstOrNull()?.name} requests=$requests")
            assertTrue(brands.none { it.count <= 0 }, "ASSERT_HIDE_EMPTY_BRANDS invalid=${brands.filter { it.count <= 0 }} requests=$requests")
            assertEquals(101, brands.map { it.id }.distinct().size, "ASSERT_DEDUPED_BRAND_IDS expected=101 actual=${brands.map { it.id }.distinct().size} requests=$requests")
            assertTrue(requests.any { it.contains("hide_empty=true") }, "ASSERT_BRAND_REQUEST_HIDE_EMPTY requests=$requests")
            controller.selectBrand(brands.first())
            advanceUntilIdle()
            assertEquals(1, controller.state.value.paging.currentPage, "ASSERT_PRODUCTS_PAGE1_CURRENT_PAGE expected=1 state=${controller.state.value} requests=$requests")
            assertEquals(12, controller.state.value.paging.items.size, "ASSERT_PRODUCTS_PAGE1_SIZE expected=12 state=${controller.state.value} requests=$requests")
            assertTrue(requests.any { it.contains("brand=alpha") && it.contains("per_page=12") && !it.contains("tag=") }, "ASSERT_SELECTED_BRAND_QUERY requests=$requests")
            controller.loadNextPage()
            advanceUntilIdle()
            assertEquals(2, controller.state.value.paging.currentPage, "ASSERT_PRODUCTS_PAGE2_CURRENT_PAGE expected=2 state=${controller.state.value} requests=$requests")
            assertEquals(13, controller.state.value.paging.items.size, "ASSERT_PRODUCTS_PAGE2_SIZE expected=13 state=${controller.state.value} requests=$requests")
            controller.setSort(StoreProductSortOption.NAME_ASC)
            controller.setSizes(setOf("M"))
            controller.setColors(setOf("Azul"))
            assertEquals(StoreProductSortOption.NAME_ASC, controller.state.value.sort, "ASSERT_SORT_PRESERVED expected=${StoreProductSortOption.NAME_ASC} actual=${controller.state.value.sort} state=${controller.state.value}")
            assertEquals(setOf("M"), controller.state.value.selectedSizes, "ASSERT_SIZE_FILTER_PRESERVED expected=${setOf("M")} actual=${controller.state.value.selectedSizes} state=${controller.state.value}")
            assertEquals(setOf("Azul"), controller.state.value.selectedColors, "ASSERT_COLOR_FILTER_PRESERVED expected=${setOf("Azul")} actual=${controller.state.value.selectedColors} state=${controller.state.value}")
            controller.backToBrands()
            controller.selectBrand(brands.first())
            advanceUntilIdle()
            assertEquals(2, controller.state.value.paging.currentPage, "ASSERT_RESELECT_PAGE_PRESERVED expected=2 state=${controller.state.value} requests=$requests")
            assertEquals(13, controller.state.value.paging.items.size, "ASSERT_RESELECT_PRODUCTS_PRESERVED expected=13 state=${controller.state.value} requests=$requests")
            assertEquals(StoreProductSortOption.NAME_ASC, controller.state.value.sort, "ASSERT_RESELECT_SORT_PRESERVED expected=${StoreProductSortOption.NAME_ASC} actual=${controller.state.value.sort}")
            assertEquals(setOf("M"), controller.state.value.selectedSizes, "ASSERT_RESELECT_SIZE_PRESERVED expected=${setOf("M")} actual=${controller.state.value.selectedSizes}")
            assertEquals(setOf("Azul"), controller.state.value.selectedColors, "ASSERT_RESELECT_COLOR_PRESERVED expected=${setOf("Azul")} actual=${controller.state.value.selectedColors}")
        } catch (failure: AssertionError) {
            println("BRANDS_DIAGNOSTIC_FAILURE=${failure.message}")
            throw failure
        } finally { client.close() }
    }
}
