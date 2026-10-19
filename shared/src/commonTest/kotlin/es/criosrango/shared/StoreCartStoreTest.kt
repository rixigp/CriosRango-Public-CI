package es.criosrango.shared

import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class StoreCartStoreTest {
    @Test
    fun sessionStoreKeepsCartCredentialsAcrossReads() {
        val session = InMemoryStoreSessionStore()
        session.cartToken = "cart-token"
        session.nonce = "nonce"
        session.cookieHeader = "wordpress_logged_in=x"
        assertEquals("cart-token", session.cartToken)
        assertEquals("nonce", session.nonce)
        assertEquals("wordpress_logged_in=x", session.cookieHeader)
    }

    @Test
    fun clearInvalidatesAllPersistedStoreApiCredentials() {
        val session = InMemoryStoreSessionStore(
            cartToken = "old-cart-token",
            nonce = "old-nonce",
            cookieHeader = "wp_woocommerce_session=old"
        )

        session.clear()

        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
    }

    @Test
    fun emptySessionStartsWithoutCartCredentials() {
        val session: StoreSessionStore = InMemoryStoreSessionStore()
        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
    }

    @Test
    fun initialUnknownCartLoadsAndEmptySuccessBecomesKnownEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)

        assertEquals(StoreCartLoadState.LOADING, store.state.value)
        store.refresh()
        advanceUntilIdle()

        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())
        assertNull(store.error.value)
    }

    @Test
    fun initialPopulatedSuccessBecomesKnownPopulated() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to populatedCartJson
        ), this)

        store.refresh()
        advanceUntilIdle()

        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)
        assertEquals("line-1", store.cart.value.items.single().key)
    }

    @Test
    fun initialFetchErrorEndsInErrorAndRetryCanResolveEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.ServiceUnavailable to """{"code":"offline","message":"offline"}""",
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)

        store.refresh()
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.ERROR, store.state.value)
        assertNotNull(store.error.value)

        store.refresh()
        assertEquals(StoreCartLoadState.LOADING, store.state.value)
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertNull(store.error.value)
    }

    @Test
    fun knownEmptyReentryDoesNotShowLoadingAndPreservesEmptyOnRefreshError() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        var requests = 0
        val api = StoreApiClient(client = HttpClient(MockEngine {
            if (requests++ == 0) {
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                respond("""{"code":"offline","message":"offline"}""", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val store = StoreCartStore(api, this)
        store.refresh()
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)

        store.refresh()
        runCurrent()
        assertTrue(refreshStarted.isCompleted)
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())

        finishRefresh.complete(Unit)
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertNotNull(store.error.value)
    }

    @Test
    fun knownPopulatedReentryKeepsSnapshotVisibleUntilRefreshCompletes() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        var requests = 0
        val api = StoreApiClient(client = HttpClient(MockEngine {
            if (requests++ == 0) {
                respond(populatedCartJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val store = StoreCartStore(api, this)
        store.refresh()
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)

        store.refresh()
        runCurrent()
        assertTrue(refreshStarted.isCompleted)
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)
        assertEquals("line-1", store.cart.value.items.single().key)

        finishRefresh.complete(Unit)
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
    }

    @Test
    fun removingLastLineProducesKnownEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to populatedCartJson,
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)
        store.refresh()
        advanceUntilIdle()
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)

        store.remove(StoreCartLine(key = "line-1", id = 1, name = "Camisa", quantity = 1))
        advanceUntilIdle()

        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())
    }

    private fun apiWithResponses(vararg responses: Pair<HttpStatusCode, String>): StoreApiClient {
        var index = 0
        val client = HttpClient(MockEngine {
            val response = responses[index.coerceAtMost(responses.lastIndex)]
            if (index < responses.lastIndex) index++
            respond(
                content = response.second,
                status = response.first,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        return StoreApiClient(client = client)
    }

    private companion object {
        const val populatedCartJson = """{"items":[{"key":"line-1","id":1,"name":"Camisa","quantity":1}]}"""
    }

}
