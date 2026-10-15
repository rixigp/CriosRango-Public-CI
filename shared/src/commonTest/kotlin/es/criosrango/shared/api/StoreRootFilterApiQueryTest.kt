package es.criosrango.shared.api

import es.criosrango.shared.model.StoreProductAttributeFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreRootFilterApiQueryTest {
    @Test fun root_filter_criteria_use_paginated_store_api_query_parameters() = kotlinx.coroutines.test.runTest {
        var requestPath = ""
        var sizeTaxonomy: String? = null
        var sizeTermIds: String? = null
        var tagIds: String? = null
        val http = HttpClient(MockEngine { request ->
            requestPath = request.url.encodedPath
            sizeTaxonomy = request.url.parameters["attributes[0][attribute]"]
            sizeTermIds = request.url.parameters["attributes[0][term_id]"]
            tagIds = request.url.parameters["tag"]
            respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val api = StoreApiClient(client = http)
        api.products(perPage = 24, page = 2, category = 445,
            attributeFilters = listOf(StoreProductAttributeFilter("pa_tallas", listOf(4, 5))),
            tagIds = listOf(12, 14))
        assertEquals("/wp-json/wc/store/v1/products", requestPath)
        assertEquals("pa_tallas", sizeTaxonomy)
        assertEquals("4,5", sizeTermIds)
        assertEquals("12,14", tagIds)
        api.close()
    }
}
