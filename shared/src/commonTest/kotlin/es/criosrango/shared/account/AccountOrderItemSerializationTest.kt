package es.criosrango.shared.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class AccountOrderItemSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldPayloadWithoutNewFields_remainsCompatible() {
        val item = json.decodeFromString<AccountOrderItem>(
            """{"name":"Body","quantity":1,"variations":[]}"""
        )
        assertEquals("Body", item.name)
        assertEquals(1, item.quantity)
        assertNull(item.productId)
        assertNull(item.imageUrl)
    }

    @Test
    fun newPayload_decodesProductId() {
        val item = json.decodeFromString<AccountOrderItem>(
            """{"name":"Body","quantity":1,"variations":[],"product_id":57392}"""
        )
        assertEquals(57392, item.productId)
    }

    @Test
    fun newPayload_decodesImageUrl() {
        val item = json.decodeFromString<AccountOrderItem>(
            """{"name":"Body","quantity":1,"variations":[],"image_url":"https://criosrango.es/wp-content/uploads/item-300x300.jpg"}"""
        )
        assertEquals("https://criosrango.es/wp-content/uploads/item-300x300.jpg", item.imageUrl)
    }

    @Test
    fun explicitNullNewFields_areAccepted() {
        val item = json.decodeFromString<AccountOrderItem>(
            """{"name":"Body","quantity":1,"variations":[],"product_id":null,"image_url":null}"""
        )
        assertNull(item.productId)
        assertNull(item.imageUrl)
    }
}
