package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import es.criosrango.shared.model.StoreShippingOption

class ShippingPresentationTest {
    @Test
    fun usesMethodAndLabelSemanticsWithoutChangingBackendIds() {
        assertEquals("Envío a domicilio", resolveShippingPresentationName("free_shipping", "free_shipping:3", "Envío gratuito"))
        assertEquals("Envío a Tarancón", resolveShippingPresentationName("flat_rate", "flat_rate:4", "Envío a Tarancón gratuito"))
        assertEquals("Recogida en tienda", resolveShippingPresentationName("local_pickup", "local_pickup:6", "Recogida local Tarancón gratuito"))
        assertEquals("Correos Express", resolveShippingPresentationName("flat_rate", "flat_rate:4", "Correos Express gratuito"))
    }

    @Test
    fun stripsRedundantFreeWordingForFallbackLabels() {
        assertEquals("Entrega a domicilio", resolveShippingPresentationName("custom_method", "custom_method:1", "Entrega a domicilio gratuito"))
    }

    @Test
    fun presentationKeepsAuthoritativeRateFieldsUnchanged() {
        val raw = """{"rate_id":"local_pickup:6","name":"Recogida local Tarancón gratuito","method_id":"local_pickup","price":"0","selected":true}"""
        val parsed = Json.decodeFromString<StoreShippingOption>(raw)
        assertEquals("local_pickup:6", parsed.rateId)
        assertEquals("local_pickup", parsed.methodId)
        assertTrue(parsed.selected)
        assertEquals("0", parsed.price)
    }
}
