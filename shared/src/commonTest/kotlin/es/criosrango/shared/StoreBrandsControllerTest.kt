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
    fun controllerLoadsAllBrandPages() = runTest {
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
            assertEquals(101, brands.size)
            assertEquals("Alpha", brands.first().name)
            assertTrue(brands.none { it.count <= 0 })
            assertEquals(101, brands.map { it.id }.distinct().size)
            assertTrue(requests.any { it.contains("hide_empty=true") })
            assertTrue(requests.any { it.contains("/products/brands") && it.contains("page=1") })
            assertTrue(requests.any { it.contains("/products/brands") && it.contains("page=2") })
        } finally { client.close() }
    }

    @Test
    fun controllerLoadsFirstSelectedBrandPage() = runTest {
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
            advanceUntilIdle()
            val brands = controller.state.value.brands
            controller.selectBrand(brands.first())
            advanceUntilIdle()
            assertEquals(1, controller.state.value.paging.currentPage)
            assertEquals(12, controller.state.value.paging.items.size)
            assertTrue(requests.any { it.contains("brand=alpha") && it.contains("per_page=12") && it.contains("page=1") && !it.contains("tag=") })
        } finally { client.close() }
    }

    @Test
    fun controllerLoadsNextSelectedBrandPage() = runTest {
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
            advanceUntilIdle()
            val brands = controller.state.value.brands
            controller.selectBrand(brands.first())
            advanceUntilIdle()
            controller.loadNextPage()
            advanceUntilIdle()
            assertEquals(2, controller.state.value.paging.currentPage)
            assertEquals(13, controller.state.value.paging.items.size)
            assertEquals(13, controller.state.value.paging.items.map { it.id }.distinct().size)
            assertTrue(requests.any { it.contains("brand=alpha") && it.contains("per_page=12") && it.contains("page=2") && !it.contains("tag=") })
        } finally { client.close() }
    }
}
