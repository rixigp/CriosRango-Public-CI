package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreProductAttributeFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreRootFilterOwnerTest {
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
        val owner = StoreRootFilterOwner(api, CoroutineScope(StandardTestDispatcher(testScheduler)))
        owner.open(StoreRootFilterScope.CATALOG)
        advanceUntilIdle()
        assertEquals(0, products)
        val options = (owner.state.value.optionsState as StoreFilterOptionsState.Ready).options
        assertEquals(4, options.sizes.single().id); assertEquals("pa_tallas", options.sizes.single().taxonomy)
        assertEquals("m", options.sizes.single().slug)
        assertEquals(9, options.colors.single().id); assertEquals(12, options.brands.single().id)
        owner.changeSizes(setOf("M")); owner.changeColors(setOf("Negro")); owner.changeBrands(setOf("Marca"))
        assertEquals(0, products)
        owner.cancel()
        assertEquals(0, products)
        owner.open(StoreRootFilterScope.OUTLET)
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
        val client = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("/products/attributes") -> {
                    requests++
                    respond(if (requests == 1) """[{"id":3,"name":"Size","taxonomy":"pa_size"}]""" else """[{"id":17,"name":"Tallas","taxonomy":"pa_tallas"},{"id":29,"name":"Color","taxonomy":"pa_color"}]""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                }
                req.url.encodedPath.endsWith("/products/attributes/17/terms") -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                req.url.encodedPath.endsWith("/products/attributes/29/terms") -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
                req.url.encodedPath.endsWith("/products/tags") -> respond("[]", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
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
        val owner = StoreRootFilterOwner(api, CoroutineScope(StandardTestDispatcher(testScheduler)))
        owner.open(StoreRootFilterScope.OUTLET)
        advanceUntilIdle()
        val error = owner.state.value.optionsState as StoreFilterOptionsState.Error
        assertTrue(error.message.contains("Size"))
        assertFalse(owner.state.value.resultsVisible)
        owner.retry()
        advanceUntilIdle()
        assertTrue(owner.state.value.optionsState is StoreFilterOptionsState.Ready)
        assertEquals(StoreRootFilterScope.OUTLET, owner.state.value.scope)
        api.close()
    }
}
