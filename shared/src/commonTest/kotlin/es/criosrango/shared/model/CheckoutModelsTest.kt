package es.criosrango.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckoutModelsTest {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    @Test
    fun customerAddressSerializesWooFields() {
        val address = CustomerAddress(
            firstName = "Ana", lastName = "Ruiz", email = "ana@example.test",
            phone = "600000000", address1 = "Calle Mayor 1", postcode = "28001",
            city = "Madrid", state = "M", country = "ES"
        )
        val encoded = json.encodeToString(address)
        assertTrue(encoded.contains("""first_name":"Ana"""))
        assertTrue(encoded.contains("""last_name":"Ruiz"""))
        assertTrue(encoded.contains("""address_1":"Calle Mayor 1"""))
        assertTrue(encoded.contains("""postcode":"28001"""))
        assertFalse(encoded.contains("firstName"))
    }

    @Test
    fun updateCustomerSerializesBillingAndShippingAddresses() {
        val address = CustomerAddress(firstName = "Ana", lastName = "Ruiz", email = "ana@example.test")
        val encoded = json.encodeToString(UpdateCustomerRequest(address))
        assertTrue(encoded.contains("billing_address"))
        assertTrue(encoded.contains("shipping_address"))
        assertTrue(encoded.contains("""first_name":"Ana"""))
    }

    @Test
    fun shippingRateParsesAndKeepsSelectedRate() {
        val raw = """{"package_id":0,"name":"Entrega","destination":{"address_1":"Calle Mayor 1","city":"Madrid","state":"M","postcode":"28001","country":"ES"},"shipping_rates":[{"rate_id":"flat_rate:1","name":"Correos Express","method_id":"flat_rate","price":"0","taxes":"0","selected":true},{"rate_id":"local:2","name":"Otra","method_id":"local","price":"500","taxes":"105","selected":false}]}"""
        val parsed = json.decodeFromString<StoreShippingPackage>(raw)
        assertEquals(2, parsed.rates.size)
        assertEquals("flat_rate:1", parsed.rates.first { it.selected }.rateId)
    }

    @Test
    fun paymentNormalizationExcludesUnsupportedAndDeduplicatesCard() {
        val options = normalizePaymentGatewayIds(listOf("cecabank_gateway", "redsys", "cecabank_gateway", "cod", "cheque"))
        assertEquals(2, options.size)
        assertEquals(CheckoutPaymentKind.CARD, options[0].kind)
        assertEquals("cecabank_gateway", options[0].gatewayId)
        assertEquals(CheckoutPaymentKind.BIZUM, options[1].kind)
        assertEquals("cheque", options[1].gatewayId)
    }

    @Test
    fun createOrderSerializesExpectedWooCheckoutPayload() {
        val address = CustomerAddress(firstName = "Ana", lastName = "Ruiz", email = "ana@example.test", country = "ES")
        val request = CreateOrderRequest(
            paymentMethod = "cecabank_gateway",
            billingAddress = address,
            shippingAddress = address,
            shippingRate = "flat_rate:1",
            expectedTotal = "10900",
            paymentData = mapOf("token" to "test-token"),
            customerNote = "Entregar por la tarde"
        )
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains("""payment_method":"cecabank_gateway"""))
        assertTrue(encoded.contains("billing_address"))
        assertTrue(encoded.contains("shipping_address"))
        assertTrue(encoded.contains("""shipping_rate":"flat_rate:1"""))
        assertTrue(encoded.contains("""expected_total":"10900"""))
        assertTrue(encoded.contains("""payment_data":{"token":"test-token"}"""))
        assertTrue(encoded.contains("""customer_note":"Entregar por la tarde"""))
    }
}
