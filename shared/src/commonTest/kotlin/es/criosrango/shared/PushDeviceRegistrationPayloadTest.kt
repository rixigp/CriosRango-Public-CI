package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PushDeviceRegistrationPayloadTest {
    private val json = Json

    @Test
    fun registrationPayloadSerializesOnAndOffAsJsonBooleans() {
        assertRegistrationPayload(newProducts = true, orderUpdates = false)
        assertRegistrationPayload(newProducts = false, orderUpdates = true)
    }

    private fun assertRegistrationPayload(newProducts: Boolean, orderUpdates: Boolean) {
        val payload = PushDeviceRegistrationPayload(
            platform = "ios",
            identifierType = "apns_token",
            identifier = "test-token",
            newProducts = newProducts,
            orderUpdates = orderUpdates
        )

        val encoded = json.encodeToString(payload)
        val fields = json.parseToJsonElement(encoded).jsonObject
        assertEquals("ios", fields["platform"]?.jsonPrimitive?.content)
        assertEquals("apns_token", fields["identifier_type"]?.jsonPrimitive?.content)
        assertEquals("test-token", fields["identifier"]?.jsonPrimitive?.content)

        val serializedNewProducts = fields["new_products"]?.jsonPrimitive
        val serializedOrderUpdates = fields["order_updates"]?.jsonPrimitive
        assertNotNull(serializedNewProducts)
        assertNotNull(serializedOrderUpdates)
        assertEquals(newProducts, serializedNewProducts.booleanOrNull)
        assertEquals(orderUpdates, serializedOrderUpdates.booleanOrNull)
        assertFalse(serializedNewProducts.isString)
        assertFalse(serializedOrderUpdates.isString)
    }
}
