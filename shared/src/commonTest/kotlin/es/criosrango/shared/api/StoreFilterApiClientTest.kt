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
}
