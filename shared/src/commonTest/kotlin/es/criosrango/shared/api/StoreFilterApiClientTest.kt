package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoreFilterApiClientTest {
    @Test fun query_preserves_scope_context_canonical_ids_pagination_sort_and_facet() = runTest {
        var requestPath = ""
        var query = emptyMap<String, String>()
        val http = HttpClient(MockEngine { request ->
            requestPath = request.url.encodedPath
            query = request.url.parameters.names().associateWith { request.url.parameters[it] ?: "" }
            respond(
                """{"schema_version":1,"page":2,"per_page":12,"total":25,"has_more":true,"product_ids":[39291,46553],"facets":{}}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json")
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val client = StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
        val response = client.query(
            scope = "outlet",
            outletCategoryId = 477,
            originCategoryIds = listOf(287, 78, 287),
            sizeIds = listOf(342, 342),
            colorIds = listOf(353),
            brandIds = listOf(777),
            page = 2,
            perPage = 12,
            sort = "price_asc",
            facet = "sizes"
        )
        assertEquals("/query", requestPath)
        assertEquals("outlet", query["scope"])
        assertEquals("477", query["outlet_category_id"])
        assertEquals("78,287", query["origin_category_ids"])
        assertEquals("342", query["size_ids"])
        assertEquals("353", query["color_ids"])
        assertEquals("777", query["brand_ids"])
        assertFalse("allowed_product_ids" in query)
        assertEquals("2", query["page"])
        assertEquals("12", query["per_page"])
        assertEquals("price_asc", query["sort"])
        assertEquals("sizes", query["facet"])
        assertEquals(listOf(39291, 46553), response.productIds)
        assertEquals(25, response.total)
        assertEquals(true, response.hasMore)
        client.close()
    }

    @Test fun null_category_and_empty_origins_are_omitted() = runTest {
        var query = emptyMap<String, String>()
        val http = HttpClient(MockEngine { request ->
            query = request.url.parameters.names().associateWith { request.url.parameters[it] ?: "" }
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{}}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json")
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val client = StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
        client.query(scope = "catalog")
        assertFalse("outlet_category_id" in query)
        assertFalse("origin_category_ids" in query)
        client.close()
    }

    @Test fun allowed_product_ids_are_sorted_deduplicated_and_omitted_when_empty() = runTest {
        var query = emptyMap<String, String>()
        val http = HttpClient(MockEngine { request ->
            query = request.url.parameters.names().associateWith { request.url.parameters[it] ?: "" }
            respond("""{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{}}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val client = StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
        client.query(scope = "catalog", allowedProductIds = emptyList())
        assertFalse("allowed_product_ids" in query)
        client.query(scope = "catalog", allowedProductIds = listOf(789, 123, 456, 123, 789))
        assertEquals("123,456,789", query["allowed_product_ids"])
        client.close()
    }

    @Test fun query_decodes_product_ids_has_more_and_total_directly() = runTest {
        val http = HttpClient(MockEngine {
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":13,"has_more":true,"product_ids":[1001,1002,1003],"facets":{"sizes":[],"colors":[],"brands":[]}}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json")
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val client = StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
        val response = client.query(scope = "catalog", page = 1, perPage = 12, sort = "newest")
        assertEquals(listOf(1001, 1002, 1003), response.productIds)
        assertEquals(true, response.hasMore)
        assertEquals(13, response.total)
        client.close()
    }

    @Test fun query_records_network_deserialization_and_safe_metadata() = runTest {
        val records=mutableListOf<StoreRequestTiming>()
        val http=HttpClient(MockEngine{respond("""{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{}}""",HttpStatusCode.OK,headersOf("Content-Type","application/json"))}){install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        val client=StoreFilterApiClient("https://example.test/wp-json/criosrango-filter/v1/query",http,StoreRequestTimingRecorder{records+=it})
        client.query(scope="outlet",outletCategoryId=446,page=1,perPage=12);val r=records.single()
        assertEquals("outlet_query",r.requestType);assertEquals("/wp-json/criosrango-filter/v1/query",r.path);assertEquals(200,r.httpStatus)
        assertTrue(r.success);assertFalse(r.timeout);assertTrue(r.totalOperationMs>=r.networkMs);assertTrue(r.networkMs>=0);assertTrue(r.deserializationMs>=0)
        assertEquals(1,r.page);assertEquals(12,r.perPage);assertEquals("outlet",r.scope)
        assertFalse(r.path.contains("Authorization",true));assertFalse(r.path.contains("token",true));assertFalse(r.path.contains("@"));client.close()
    }
    @Test fun query_records_http_error_status() = runTest {
        val records=mutableListOf<StoreRequestTiming>()
        val http=HttpClient(MockEngine{respond("""{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{}}""",HttpStatusCode.InternalServerError,headersOf("Content-Type","application/json"))}){install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        val client=StoreFilterApiClient("https://example.test/wp-json/criosrango-filter/v1/query",http,StoreRequestTimingRecorder{records+=it})
        client.query(scope="outlet");assertEquals(500,records.single().httpStatus);assertFalse(records.single().success);assertEquals("HTTP_ERROR",records.single().errorType);client.close()
    }
    @Test fun query_marks_timeout_without_recording_exception_text_or_url() = runTest {
        class SimulatedTimeoutException:Exception("timeout for user@example.com token=secret")
        val records=mutableListOf<StoreRequestTiming>()
        val http=HttpClient(MockEngine{throw SimulatedTimeoutException()}){install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        val client=StoreFilterApiClient("https://example.test/wp-json/criosrango-filter/v1/query",http,StoreRequestTimingRecorder{records+=it})
        val error=runCatching{client.query(scope="outlet")}.exceptionOrNull();assertNotNull(error);val r=records.single()
        assertTrue(r.timeout);assertFalse(r.success);assertEquals("TIMEOUT",r.errorType);assertEquals("/wp-json/criosrango-filter/v1/query",r.path)
        assertFalse(r.path.contains("example.com"));assertFalse(r.path.contains("secret"));client.close()
    }

}
