package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
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
}
