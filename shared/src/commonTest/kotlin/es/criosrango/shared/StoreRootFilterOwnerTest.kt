package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
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
        val owner = StoreRootFilterOwner(api, ownerScope())

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
        val owner = StoreRootFilterOwner(api, ownerScope())

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
        val owner = StoreRootFilterOwner(api, ownerScope())
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


    @Test fun root_filter_pagination_preserves_page_size_filter_ids_and_scope() = runTest {
        val productRequests = mutableListOf<Map<String, String?>>()
        val client = HttpClient(MockEngine { req ->
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
                    productRequests += mapOf(
                        "page" to req.url.parameters["page"],
                        "per_page" to req.url.parameters["per_page"],
                        "category" to req.url.parameters["category"],
                        "size_taxonomy" to req.url.parameters["attributes[0][attribute]"],
                        "size_ids" to req.url.parameters["attributes[0][term_id]"],
                        "color_taxonomy" to req.url.parameters["attributes[1][attribute]"],
                        "color_ids" to req.url.parameters["attributes[1][term_id]"],
                        "tag_ids" to req.url.parameters["tag"]
                    )
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
        val owner = StoreRootFilterOwner(api, ownerScope())
        val ready = observeOptionsState(owner) { it is StoreFilterOptionsState.Ready }
        owner.open(StoreRootFilterScope.CATALOG)
        assertIs<StoreFilterOptionsState.Ready>(ready.await())

        owner.changeSizes(setOf("3XL"))
        owner.changeColors(setOf("Azul"))
        owner.changeBrands(setOf("Marca"))
        assertTrue(owner.apply())

        val paginator = CatalogPaginatorStore<StoreProduct>(this) { it.id }
        var loaderEntryCount = 0
        suspend fun loadCatalogPage(page: Int, perPage: Int): CatalogPage<StoreProduct> {
            loaderEntryCount++
            val items = owner.loadProducts(page, perPage)
            return CatalogPage(items, hasMore = page == 1)
        }
        paginator.start(owner.queryKey(), ::loadCatalogPage)
        advanceUntilIdle()
        assertEquals(
            "LOADER_ENTRY_COUNT=$loaderEntryCount; " +
                "PAGINATOR_INITIAL_ERROR=${paginator.state.value.initialError?.let { it::class.simpleName + ": " + it.message }}; " +
                "PAGINATOR_ITEMS=${paginator.state.value.items.size}; " +
                "PAGINATOR_HAS_MORE=${paginator.state.value.hasMore}; " +
                "PAGINATOR_IS_INITIAL_LOADING=${paginator.state.value.isInitialLoading}; " +
                "OWNER_QUERY_KEY=${owner.queryKey()}; " +
                "OWNER_STATE=${owner.state.value}; " +
                "OWNER_APPLIED_SIZES=${owner.state.value.appliedSizes}; " +
                "OWNER_APPLIED_COLORS=${owner.state.value.appliedColors}; " +
                "OWNER_APPLIED_BRANDS=${owner.state.value.appliedBrands}; " +
                "RESOLVED_SIZE_IDS=${owner.queryAttributeFilters().firstOrNull { it.taxonomy == "pa_tallas" }?.termIds}; " +
                "RESOLVED_COLOR_IDS=${owner.queryAttributeFilters().firstOrNull { it.taxonomy == "pa_color" }?.termIds}; " +
                "RESOLVED_BRAND_IDS=${owner.queryTagIds()}",
            1,
            productRequests.size
        )
        paginator.loadNext(::loadCatalogPage)
        advanceUntilIdle()

        assertEquals(2, productRequests.size)
        val catalogFirst = productRequests[0]
        assertEquals("1", catalogFirst["page"])
        assertEquals("12", catalogFirst["per_page"])
        assertEquals(null, catalogFirst["category"])
        assertEquals("pa_tallas", catalogFirst["size_taxonomy"])
        assertEquals("342", catalogFirst["size_ids"])
        assertEquals("pa_color", catalogFirst["color_taxonomy"])
        assertEquals("353", catalogFirst["color_ids"])
        assertEquals("777", catalogFirst["tag_ids"])

        val catalogSecond = productRequests[1]
        assertEquals("2", catalogSecond["page"])
        assertEquals("12", catalogSecond["per_page"])
        assertEquals(null, catalogSecond["category"])
        assertEquals("342", catalogSecond["size_ids"])
        assertEquals("353", catalogSecond["color_ids"])
        assertEquals("777", catalogSecond["tag_ids"])

        owner.open(StoreRootFilterScope.OUTLET)
        owner.changeSizes(setOf("3XL"))
        owner.changeColors(setOf("Azul"))
        owner.changeBrands(setOf("Marca"))
        assertTrue(owner.apply())
        paginator.start(owner.queryKey()) { page, perPage ->
            CatalogPage(owner.loadProducts(page, perPage), hasMore = false)
        }
        advanceUntilIdle()

        assertEquals(3, productRequests.size)
        val outletFirst = productRequests[2]
        assertEquals("1", outletFirst["page"])
        assertEquals("12", outletFirst["per_page"])
        assertEquals("445", outletFirst["category"])
        assertEquals("pa_tallas", outletFirst["size_taxonomy"])
        assertEquals("342", outletFirst["size_ids"])
        assertEquals("pa_color", outletFirst["color_taxonomy"])
        assertEquals("353", outletFirst["color_ids"])
        assertEquals("777", outletFirst["tag_ids"])
        api.close()
    }

}
