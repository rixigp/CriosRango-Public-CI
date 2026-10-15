package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreFilterApiClient
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreProductAttributeFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoreRootFilterOwnerTest {
    /**
     * Keep owner jobs on the test scheduler and under backgroundScope so their
     * lifecycle is owned by runTest and cancelled deterministically at teardown.
     */
    private fun TestScope.ownerScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob(backgroundScope.coroutineContext[Job]) +
                StandardTestDispatcher(testScheduler)
        )

    private fun testFilterApi(): StoreFilterApiClient {
        val http = HttpClient(MockEngine {
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{"sizes":[],"colors":[],"brands":[]}}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
    }

    private fun TestScope.observeOptionsState(
        owner: StoreRootFilterOwner,
        predicate: (StoreFilterOptionsState) -> Boolean
    ) = async(start = CoroutineStart.UNDISPATCHED) {
        owner.state.map { it.optionsState }.first(predicate)
    }

    @Test fun metadata_is_global_cached_and_root_draft_never_requests_products() = runTest {
        var products = 0; var attrs = 0; var sizeTerms = 0; var colorTerms = 0; var tags = 0
        val client = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/products/attributes") -> { attrs++; respond("""[{"id":17,"name":"TÁLLAS","taxonomy":"pa_tallas"},{"id":29,"name":"Color","taxonomy":"pa_color"}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                req.url.encodedPath.endsWith("/products/attributes/17/terms") -> { sizeTerms++; respond("""[{"id":4,"name":"M","slug":"m","count":2}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                req.url.encodedPath.endsWith("/products/attributes/29/terms") -> { colorTerms++; respond("""[{"id":9,"name":"Negro","slug":"negro","count":2}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                req.url.encodedPath.endsWith("/products/tags") -> { tags++; respond("""[{"id":12,"name":"Marca","slug":"marca","count":1}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                req.url.encodedPath.endsWith("/products") -> { products++; respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                else -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
            }
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
        val api = StoreApiClient(client = client)
        val owner = StoreRootFilterOwner(api, ownerScope(), testFilterApi())

        val initialReady = observeOptionsState(owner) { it is StoreFilterOptionsState.Ready }
        owner.open(StoreRootFilterScope.CATALOG)
        val options = assertIs<StoreFilterOptionsState.Ready>(initialReady.await()).options
        assertEquals(0, products)
        assertEquals(4, options.sizes.single().id); assertEquals("pa_tallas", options.sizes.single().taxonomy)
        assertEquals("m", options.sizes.single().slug)
        assertEquals(9, options.colors.single().id); assertEquals(12, options.brands.single().id)

        owner.changeSizes(setOf("M")); owner.changeColors(setOf("Negro")); owner.changeBrands(setOf("Marca"))
        assertEquals(0, products)
        owner.cancel()
        assertEquals(0, products)

        val reopenedReady = observeOptionsState(owner) { it is StoreFilterOptionsState.Ready }
        owner.open(StoreRootFilterScope.OUTLET)
        assertIs<StoreFilterOptionsState.Ready>(reopenedReady.await())
        assertEquals(1, attrs); assertEquals(1, sizeTerms); assertEquals(1, colorTerms); assertEquals(1, tags)
        owner.apply()
        assertFalse(owner.state.value.resultsVisible); assertEquals(0, products)
        owner.changeSizes(setOf("M"))
        assertTrue(owner.apply()); assertTrue(owner.state.value.resultsVisible)
        assertEquals(listOf(StoreProductAttributeFilter("pa_tallas", listOf(4))), owner.queryAttributeFilters())
        assertEquals(0, products) // paginated host starts requests only after the results surface enters composition.
        owner.removeAppliedSize("M")
        assertFalse(owner.state.value.resultsVisible)
        owner.open(StoreRootFilterScope.CATALOG)
        assertEquals(StoreRootFilterScope.CATALOG, owner.state.value.scope)
        owner.backFromResults()
        assertFalse(owner.state.value.resultsVisible)
        api.close()
    }

    @Test fun missing_global_attribute_reports_the_actual_metadata_names_and_can_retry() = runTest {
        var requests = 0
        var products = 0
        val client = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/products/attributes") -> {
                    requests++
                    respond(if (requests == 1) """[{"id":3,"name":"Size","taxonomy":"pa_size"}]""" else """[{"id":17,"name":"Tallas","taxonomy":"pa_tallas"},{"id":29,"name":"Color","taxonomy":"pa_color"}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                }
                req.url.encodedPath.endsWith("/products/attributes/17/terms") -> respond("""[{"id":4,"name":"M","slug":"m","count":2}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                req.url.encodedPath.endsWith("/products/attributes/29/terms") -> respond("""[{"id":9,"name":"Negro","slug":"negro","count":2}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                req.url.encodedPath.endsWith("/products/tags") -> respond("""[{"id":12,"name":"Marca","slug":"marca","count":1}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                req.url.encodedPath.endsWith("/products") -> { products++; respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json")) }
                else -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
            }
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
        val api = StoreApiClient(client = client)
        val owner = StoreRootFilterOwner(api, ownerScope(), testFilterApi())

        val initialError = observeOptionsState(owner) { it is StoreFilterOptionsState.Error }
        owner.open(StoreRootFilterScope.OUTLET)
        val error = assertIs<StoreFilterOptionsState.Error>(initialError.await())
        assertTrue(error.message.contains("Size"))
        assertTrue(error.message.contains("atributos recibidos"))
        assertFalse(owner.state.value.resultsVisible)

        val retriedReady = observeOptionsState(owner) { it is StoreFilterOptionsState.Ready }
        owner.retry()
        val ready = assertIs<StoreFilterOptionsState.Ready>(retriedReady.await())
        assertTrue(ready.options.sizes.isEmpty().not())
        assertTrue(ready.options.colors.isEmpty().not())
        assertEquals(12, ready.options.brands.single().id)
        assertEquals(2, requests) // first attempt + retry; the handler serves updated valid metadata on retry.
        assertEquals(StoreRootFilterScope.OUTLET, owner.state.value.scope)
        assertFalse(owner.state.value.resultsVisible)
        assertEquals(0, products)
        api.close()
    }

    @Test fun duplicate_decoded_labels_keep_all_canonical_ids_for_queries() = runTest {
        var products = 0
        val client = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/products/attributes") ->
                    respond(
                        """[{"id":17,"name":"Tallas","taxonomy":"pa_tallas"},{"id":29,"name":"Color","taxonomy":"pa_color"}]""",
                        HttpStatusCode.OK, headersOf("Content-Type", "application/json")
                    )
                req.url.encodedPath.endsWith("/products/attributes/17/terms") ->
                    respond(
                        """[{"id":4,"name":"42","slug":"42","count":2},{"id":5,"name":"42","slug":"42-legacy","count":1}]""",
                        HttpStatusCode.OK, headersOf("Content-Type", "application/json")
                    )
                req.url.encodedPath.endsWith("/products/attributes/29/terms") ->
                    respond(
                        """[{"id":9,"name":"Azul","slug":"azul","count":2},{"id":10,"name":"Azul","slug":"azul-legacy","count":1}]""",
                        HttpStatusCode.OK, headersOf("Content-Type", "application/json")
                    )
                req.url.encodedPath.endsWith("/products/tags") ->
                    respond(
                        """[{"id":12,"name":"V&amp;L","slug":"v-l","count":2},{"id":13,"name":"V&L","slug":"v-l-legacy","count":1}]""",
                        HttpStatusCode.OK, headersOf("Content-Type", "application/json")
                    )
                req.url.encodedPath.endsWith("/products") -> {
                    products++
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
        }
        val api = StoreApiClient(client = client)
        val owner = StoreRootFilterOwner(api, ownerScope(), testFilterApi())
        val readyState = observeOptionsState(owner) { it is StoreFilterOptionsState.Ready }
        owner.open(StoreRootFilterScope.CATALOG)
        val options = assertIs<StoreFilterOptionsState.Ready>(readyState.await()).options

        assertEquals(listOf("42", "42"), options.sizes.map { it.name })
        assertEquals(listOf(4, 5), options.sizes.map { it.id })
        assertEquals(listOf("42", "42-legacy"), options.sizes.map { it.slug })
        assertEquals(listOf("Azul", "Azul"), options.colors.map { it.name })
        assertEquals(listOf(9, 10), options.colors.map { it.id })
        assertEquals(listOf("V&L", "V&L"), options.brands.map { it.name })
        assertEquals(listOf(12, 13), options.brands.map { it.id })
        assertEquals(listOf("V&L"), filterBrandDisplayNames(options.brands.map { it.name }, "v&l"))
        assertEquals(0, products) // local search over loaded metadata never requests products
        assertEquals(listOf("v-l", "v-l-legacy"), options.brands.map { it.slug })

        owner.changeSizes(setOf("42"))
        owner.changeColors(setOf("Azul"))
        owner.changeBrands(setOf("V&L"))
        assertEquals(0, products)
        assertTrue(owner.apply())
        assertEquals(
            listOf(
                StoreProductAttributeFilter("pa_tallas", listOf(4, 5)),
                StoreProductAttributeFilter("pa_color", listOf(9, 10))
            ),
            owner.queryAttributeFilters()
        )
        assertEquals(listOf(12, 13), owner.queryTagIds())
        assertEquals(0, products)
        api.close()
    }


    private data class RootPageFixture(
        val owner: StoreRootFilterOwner,
        val filterRequests: MutableList<Map<String, String?>>,
        val productRequests: MutableList<Map<String, String?>>,
        val api: StoreApiClient,
        val filterApi: StoreFilterApiClient
    ) {
        fun close() { api.close(); filterApi.close() }
    }

    private fun TestScope.rootPageFixture(
        pageIds: (Int) -> List<Int> = { page -> when (page) { 1 -> (1001..1012).toList(); 2 -> listOf(2001); else -> emptyList() } },
        storeResponseIds: (List<Int>) -> List<Int> = { it.reversed() },
        totalForPage: (Int) -> Int = { if (it == 1 || it == 2) 13 else 0 },
        hasMoreForPage: (Int) -> Boolean = { it == 1 }
    ): RootPageFixture {
        val productRequests = mutableListOf<Map<String, String?>>()
        val filterRequests = mutableListOf<Map<String, String?>>()
        val storeClient = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/products/attributes") ->
                    respond("""[{"id":17,"name":"Tallas","taxonomy":"pa_tallas"},{"id":29,"name":"Color","taxonomy":"pa_color"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                req.url.encodedPath.endsWith("/products/attributes/17/terms") ->
                    respond("""[{"id":342,"name":"3XL","slug":"3xl","count":29}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                req.url.encodedPath.endsWith("/products/attributes/29/terms") ->
                    respond("""[{"id":353,"name":"Azul","slug":"azul","count":3}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                req.url.encodedPath.endsWith("/products/tags") ->
                    respond("""[{"id":777,"name":"Marca","slug":"marca","count":1}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                req.url.encodedPath.endsWith("/products") -> {
                    val include = req.url.parameters["include"]
                    productRequests += mapOf(
                        "include" to include, "orderby" to req.url.parameters["orderby"],
                        "per_page" to req.url.parameters["per_page"], "page" to req.url.parameters["page"],
                        "category" to req.url.parameters["category"]
                    )
                    val requestedIds = include.orEmpty().split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
                    val returnedIds = storeResponseIds(requestedIds)
                    val body = returnedIds.joinToString(prefix = "[", postfix = "]") { id -> """{"id":$id,"name":"Synthetic product $id"}""" }
                    respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
                else -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
        }
        val filterClient = HttpClient(MockEngine { req ->
            val page = req.url.parameters["page"]?.toIntOrNull() ?: 1
            val perPage = req.url.parameters["per_page"]?.toIntOrNull() ?: 12
            val facet = req.url.parameters["facet"]
            val ids = if (facet == null) pageIds(page) else emptyList()
            filterRequests += mapOf(
                "scope" to req.url.parameters["scope"], "page" to req.url.parameters["page"],
                "per_page" to req.url.parameters["per_page"], "sort" to req.url.parameters["sort"],
                "facet" to facet, "size_ids" to req.url.parameters["size_ids"],
                "color_ids" to req.url.parameters["color_ids"], "brand_ids" to req.url.parameters["brand_ids"]
            )
            val total = if (facet == null) totalForPage(page) else 0
            val hasMore = facet == null && hasMoreForPage(page)
            val body = """{"schema_version":1,"page":$page,"per_page":$perPage,"total":$total,"has_more":$hasMore,"product_ids":[${ids.joinToString(",")}],"facets":{"sizes":[],"colors":[],"brands":[]}}"""
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
        }
        val api = StoreApiClient(client = storeClient)
        val filterApi = StoreFilterApiClient(endpoint = "https://example.test/query", client = filterClient)
        return RootPageFixture(StoreRootFilterOwner(api, ownerScope(), filterApi), filterRequests, productRequests, api, filterApi)
    }

    private suspend fun TestScope.prepareRootPageFixture(fixture: RootPageFixture) {
        fixture.owner.open(StoreRootFilterScope.CATALOG)
        val ready = observeOptionsState(fixture.owner) { it is StoreFilterOptionsState.Ready }
        assertIs<StoreFilterOptionsState.Ready>(ready.await())
        fixture.owner.changeSizes(setOf("3XL"))
        fixture.owner.changeColors(setOf("Azul"))
        fixture.owner.changeBrands(setOf("Marca"))
        assertTrue(fixture.owner.apply())
    }

    private fun RootPageFixture.catalogRequests() = filterRequests.filter { it["facet"] == null }

    @Test fun root_page_one_uses_filter_api_and_single_product_batch() = runTest {
        val fixture = rootPageFixture()
        try {
            prepareRootPageFixture(fixture)
            val page = fixture.owner.loadRootPage(1, 12, StoreProductSortOption.RECENT)
            val filters = fixture.catalogRequests()
            assertEquals(1, filters.size)
            assertEquals(mapOf("scope" to "catalog", "page" to "1", "per_page" to "12", "sort" to "newest",
                "facet" to null, "size_ids" to "342", "color_ids" to "353", "brand_ids" to "777"), filters.single())
            assertEquals(1, fixture.productRequests.size)
            assertEquals((1001..1012).joinToString(","), fixture.productRequests.single()["include"])
            assertEquals("include", fixture.productRequests.single()["orderby"])
            assertEquals("12", fixture.productRequests.single()["per_page"])
            assertEquals((1001..1012).toList(), page.items.map { it.id })
            assertEquals(12, page.items.size)
            assertTrue(page.hasMore)
            assertEquals(13, page.totalItems)
        } finally { fixture.close() }
    }

    @Test fun root_page_two_preserves_canonical_page_size_and_backend_has_more() = runTest {
        val fixture = rootPageFixture()
        try {
            prepareRootPageFixture(fixture)
            val page = fixture.owner.loadRootPage(2, 12, StoreProductSortOption.RECENT)
            val filters = fixture.catalogRequests()
            assertEquals(1, filters.size)
            assertEquals("2", filters.single()["page"])
            assertEquals("12", filters.single()["per_page"])
            assertEquals("newest", filters.single()["sort"])
            assertEquals(1, fixture.productRequests.size)
            assertEquals("2001", fixture.productRequests.single()["include"])
            assertEquals("include", fixture.productRequests.single()["orderby"])
            assertEquals("1", fixture.productRequests.single()["per_page"])
            assertEquals(listOf(2001), page.items.map { it.id })
            assertEquals(false, page.hasMore)
            assertEquals(13, page.totalItems)
        } finally { fixture.close() }
    }

    @Test fun root_page_reorders_store_batch_to_backend_ids() = runTest {
        val fixture = rootPageFixture(pageIds = { listOf(1001, 1002, 1003) })
        try {
            prepareRootPageFixture(fixture)
            val page = fixture.owner.loadRootPage(1, 12, StoreProductSortOption.RECENT)
            assertEquals("1001,1002,1003", fixture.productRequests.single()["include"])
            assertEquals(listOf(1001, 1002, 1003), page.items.map { it.id })
        } finally { fixture.close() }
    }

    @Test fun root_page_fails_when_store_batch_omits_id() = runTest {
        val fixture = rootPageFixture(pageIds = { listOf(1001, 1002, 1003) }, storeResponseIds = { listOf(1001, 1003) })
        try {
            prepareRootPageFixture(fixture)
            val error = kotlin.runCatching { fixture.owner.loadRootPage(1, 12, StoreProductSortOption.RECENT) }.exceptionOrNull()
            assertIs<es.criosrango.shared.api.StoreProductBatchConsistencyException>(error)
            assertTrue(error.message.orEmpty().contains("1002"))
            assertEquals(1, fixture.productRequests.size)
        } finally { fixture.close() }
    }

    @Test fun root_page_empty_ids_skip_store_batch() = runTest {
        val fixture = rootPageFixture(pageIds = { emptyList() }, totalForPage = { 0 }, hasMoreForPage = { false })
        try {
            prepareRootPageFixture(fixture)
            val page = fixture.owner.loadRootPage(1, 12, StoreProductSortOption.RECENT)
            assertEquals(1, fixture.catalogRequests().size)
            assertEquals(0, fixture.productRequests.size)
            assertEquals(emptyList(), page.items)
            assertEquals(false, page.hasMore)
            assertEquals(0, page.totalItems)
        } finally { fixture.close() }
    }
}
