package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreBrandsControllerTest {
    private suspend fun awaitState(
        controller: StoreBrandsController,
        predicate: (StoreBrandsState) -> Boolean
    ): StoreBrandsState = withContext(Dispatchers.Default) {
        withTimeout(5_000) { controller.state.first(predicate) }
    }

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
    fun controllerNormalizesBrandDisplayNameWithoutChangingIdentity() = runTest {
        val client = httpClient { request ->
            if (request.url.encodedPath.endsWith("/products/brands")) {
                respond(
                    """[{"id":42,"name":"Abel &amp; Lula &ndash; Kids","slug":"abel-lula","count":3}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = StoreBrandsController(
                StoreApiClient("https://example.test/wp-json/wc/store/v1/", client),
                controllerScope
            )
            awaitState(controller) { it.brands.isNotEmpty() || it.brandsError != null }
            val brand = controller.state.value.brands.single()
            assertEquals("Abel & Lula – Kids", brand.name)
            assertEquals(42, brand.id)
            assertEquals("abel-lula", brand.slug)
            controller.selectBrand(brand)
            assertEquals("Abel & Lula – Kids", controller.state.value.selectedBrand?.name)
        } finally {
            controllerScope.cancel()
            client.close()
        }
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
            val isBrandsRequest = request.url.encodedPath.endsWith("/products/brands")
            when {
                isBrandsRequest && request.url.parameters["page"] == "1" -> {
                    respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                isBrandsRequest -> {
                    respond("""[{"id":1,"name":"Duplicate","slug":"duplicate","count":9},{"id":101,"name":"Alpha","slug":"alpha","count":2},{"id":102,"name":"Empty","slug":"empty","count":0}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath.endsWith("/products") && request.url.parameters["page"] == "1" ->
                    respond((1..12).joinToString(prefix="[", postfix="]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") ->
                    respond("""[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            val controller = StoreBrandsController(api, controllerScope)
            awaitState(controller) { it.brands.isNotEmpty() || it.brandsError != null }
            assertTrue(controller.state.value.brands.isNotEmpty())
            val brands = controller.state.value.brands
            assertEquals(101, brands.size)
            assertEquals("Alpha", brands.first().name)
            assertTrue(brands.none { it.count <= 0 })
            assertEquals(101, brands.map { it.id }.distinct().size)
            assertTrue(requests.any { it.contains("hide_empty=true") })
            assertTrue(requests.any { it.contains("/products/brands") && it.contains("page=1") })
            assertTrue(requests.any { it.contains("/products/brands") && it.contains("page=2") })
        } finally { controllerScope.cancel(); client.close() }
    }

    @Test
    fun controllerLoadsFirstSelectedBrandPage() = runTest {
        val requests = mutableListOf<String>()
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
            requests += request.url.toString()
            val isBrandsRequest = request.url.encodedPath.endsWith("/products/brands")
            when {
                isBrandsRequest && request.url.parameters["page"] == "1" -> {
                    respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                isBrandsRequest -> {
                    respond("""[{"id":1,"name":"Duplicate","slug":"duplicate","count":9},{"id":101,"name":"Alpha","slug":"alpha","count":2},{"id":102,"name":"Empty","slug":"empty","count":0}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath.endsWith("/products") && request.url.parameters["page"] == "1" ->
                    respond((1..12).joinToString(prefix="[", postfix="]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") ->
                    respond("""[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            val controller = StoreBrandsController(api, controllerScope)
            awaitState(controller) { it.brands.isNotEmpty() || it.brandsError != null }
            assertTrue(controller.state.value.brands.isNotEmpty())
            val brands = controller.state.value.brands
            controller.selectBrand(brands.first())
            awaitState(controller) {
                (it.paging.currentPage == 1 && !it.paging.isInitialLoading) || it.paging.initialError != null
            }
            assertEquals(1, controller.state.value.paging.currentPage)
            assertEquals(12, controller.state.value.paging.items.size)
            assertTrue(requests.any { it.contains("brand=alpha") && it.contains("per_page=12") && it.contains("page=1") && !it.contains("tag=") })
        } finally { controllerScope.cancel(); client.close() }
    }

    @Test
    fun controllerLoadsNextSelectedBrandPage() = runTest {
        val requests = mutableListOf<String>()
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
            requests += request.url.toString()
            val isBrandsRequest = request.url.encodedPath.endsWith("/products/brands")
            when {
                isBrandsRequest && request.url.parameters["page"] == "1" -> {
                    respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                isBrandsRequest -> {
                    respond("""[{"id":1,"name":"Duplicate","slug":"duplicate","count":9},{"id":101,"name":"Alpha","slug":"alpha","count":2},{"id":102,"name":"Empty","slug":"empty","count":0}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath.endsWith("/products") && request.url.parameters["page"] == "1" ->
                    respond((1..12).joinToString(prefix="[", postfix="]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") ->
                    respond("""[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            val controller = StoreBrandsController(api, controllerScope)
            awaitState(controller) { it.brands.isNotEmpty() || it.brandsError != null }
            assertTrue(controller.state.value.brands.isNotEmpty())
            val brands = controller.state.value.brands
            controller.selectBrand(brands.first())
            awaitState(controller) {
                (it.paging.currentPage == 1 && !it.paging.isInitialLoading) || it.paging.initialError != null
            }
            controller.loadNextPage()
            awaitState(controller) {
                (it.paging.currentPage == 2 && !it.paging.isAppending) || it.paging.appendError != null
            }
            assertEquals(2, controller.state.value.paging.currentPage)
            assertEquals(13, controller.state.value.paging.items.size)
            assertEquals(13, controller.state.value.paging.items.map { it.id }.distinct().size)
            assertTrue(requests.any { it.contains("brand=alpha") && it.contains("per_page=12") && it.contains("page=2") && !it.contains("tag=") })
        } finally { controllerScope.cancel(); client.close() }
    }
    @Test
    fun storeApiClientLoadsProductsByBrandPage1() = runTest {
        var actualRequest = ""
        val client = httpClient { request ->
            actualRequest = request.url.toString()
            respond(
                (1..12).joinToString(prefix = "[", postfix = "]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" },
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        try {
            val products = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
                .products(brand = "alpha", page = 1, perPage = 12, orderBy = "date", order = "desc")
            assertEquals((1..12).toList(), products.map { it.id })
            assertEquals("alpha", io.ktor.http.Url(actualRequest).parameters["brand"])
            assertEquals("1", io.ktor.http.Url(actualRequest).parameters["page"])
            assertEquals("12", io.ktor.http.Url(actualRequest).parameters["per_page"])
            assertNull(io.ktor.http.Url(actualRequest).parameters["tag"])
            assertTrue(io.ktor.http.Url(actualRequest).parameters.names().none { it.startsWith("product_tag") })
        } finally {
            client.close()
        }
    }

    @Test
    fun storeApiClientLoadsProductsByBrandPage2() = runTest {
        var actualRequest = ""
        val client = httpClient { request ->
            actualRequest = request.url.toString()
            respond(
                """[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        try {
            val products = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
                .products(brand = "alpha", page = 2, perPage = 12, orderBy = "date", order = "desc")
            assertEquals(listOf(13), products.map { it.id })
            assertEquals("alpha", io.ktor.http.Url(actualRequest).parameters["brand"])
            assertEquals("2", io.ktor.http.Url(actualRequest).parameters["page"])
            assertEquals("12", io.ktor.http.Url(actualRequest).parameters["per_page"])
            assertNull(io.ktor.http.Url(actualRequest).parameters["tag"])
            assertTrue(io.ktor.http.Url(actualRequest).parameters.names().none { it.startsWith("product_tag") })
        } finally {
            client.close()
        }
    }

    @Test
    fun controllerSelectBrandSetsExpectedState() = runTest {
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
            when {
                request.url.encodedPath.endsWith("/products/brands") && request.url.parameters["page"] == "1" ->
                    respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products/brands") ->
                    respond("""[{"id":1,"name":"Duplicate","slug":"duplicate","count":9},{"id":101,"name":"Alpha","slug":"alpha","count":2},{"id":102,"name":"Empty","slug":"empty","count":0}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") && request.url.parameters["page"] == "1" ->
                    respond((1..12).joinToString(prefix = "[", postfix = "]") { """{"id":$it,"name":"Product-$it","prices":{"price":"1000"}}""" }, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath.endsWith("/products") ->
                    respond("""[{"id":13,"name":"Product-13","prices":{"price":"1000"}}]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = StoreBrandsController(
                StoreApiClient("https://example.test/wp-json/wc/store/v1/", client),
                controllerScope
            )
            awaitState(controller) { it.brands.isNotEmpty() || it.brandsError != null }
            val brand = controller.state.value.brands.first { it.slug == "alpha" }
            controller.selectBrand(brand)
            awaitState(controller) {
                (it.paging.currentPage == 1 && !it.paging.isInitialLoading) || it.paging.initialError != null
            }

            assertEquals("alpha", controller.state.value.selectedBrand?.slug)
            assertEquals((1..12).toList(), controller.state.value.paging.items.map { it.id })
            assertNull(controller.state.value.paging.initialError)
            assertEquals(1, controller.state.value.paging.currentPage)
            assertTrue(controller.state.value.paging.hasMore)
        } finally {
            controllerScope.cancel()
            client.close()
        }
    }

    @Test
    fun storeApiClientParsesBrandsPage1() = runTest {
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
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
        val brands = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            .productBrands(perPage = 100, page = 1)
        assertEquals(100, brands.size)
        assertEquals(1, brands.first().id)
        assertEquals("Brand-1", brands.first().name)
        assertEquals(100, brands.last().id)
        assertEquals("Brand-100", brands.last().name)
        client.close()
    }

    @Test
    fun storeApiClientParsesBrandsPage2() = runTest {
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        val client = httpClient { request ->
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
        val brands = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client)
            .productBrands(perPage = 100, page = 2)
        assertEquals(3, brands.size)
        assertEquals(1, brands[0].id)
        assertEquals("Duplicate", brands[0].name)
        assertEquals(101, brands[1].id)
        assertEquals("Alpha", brands[1].name)
        assertEquals(102, brands[2].id)
        assertEquals("Empty", brands[2].name)
        client.close()
    }

    @Test
    fun brandMockReturnsExpectedPayload() = runTest {
        val pageOneBrands = (1..100).joinToString(",") { id ->
            """{"id":$id,"name":"Brand-$id","slug":"brand-$id","count":1}"""
        }
        var matchedExpectedRequest = false
        val client = httpClient { request ->
            if (
                request.url.encodedPath == "/wp-json/wc/store/v1/products/brands" &&
                request.url.parameters["per_page"] == "100" &&
                request.url.parameters["page"] == "1" &&
                request.url.parameters["hide_empty"] == "true"
            ) {
                matchedExpectedRequest = true
                respond("[$pageOneBrands]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("UNEXPECTED_REQUEST", headers = headersOf(HttpHeaders.ContentType, "text/plain"))
            }
        }
        val body = client.get("https://example.test/wp-json/wc/store/v1/products/brands") {
            url {
                parameters.append("per_page", "100")
                parameters.append("page", "1")
                parameters.append("hide_empty", "true")
            }
        }.bodyAsText()
        assertTrue(matchedExpectedRequest)
        assertEquals("[$pageOneBrands]", body)
        client.close()
    }

}
