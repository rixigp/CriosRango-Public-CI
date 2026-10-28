package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentUrlValidatorTest {
    @Test
    fun acceptsOnlyExactAllowlistedHttpsHostsWithDynamicPathsAndQueries() {
        val validUrls = listOf(
            "https://criosrango.es/...",
            "https://www.criosrango.es/checkout/order-pay/123/?key=wc_order_123",
            "https://pgw.ceca.es/tpvweb/tpv/compra.action?order=123&token=dynamic",
            "https://tpv.ceca.es/tpvweb/tpv/compra.action?order=123",
            "https://CRIOSRANGO.ES/custom/path?dynamic=value",
            "https://pgw.ceca.es:443/tpvweb/tpv/compra.action"
        )
        validUrls.forEach { assertTrue(PaymentUrlValidator.isAllowed(it), "Expected allowed: $it") }
    }

    @Test
    fun rejectsNonHttpsAndCustomSchemes() {
        listOf(
            "http://pgw.ceca.es/pay",
            "http://criosrango.es/pay",
            "javascript:alert(1)",
            "data:text/html,hello",
            "file:///etc/passwd",
            "intent://pgw.ceca.es/pay",
            "criosrango://payment-return",
            "/checkout/order-pay/123"
        ).forEach { assertFalse(PaymentUrlValidator.isAllowed(it), "Expected rejected: $it") }
    }

    @Test
    fun rejectsUnlistedAndLookalikeHosts() {
        listOf(
            "https://evil.example/pay",
            "https://pgw.ceca.es.evil.com/pay",
            "https://pgw.ceca.es.evil.example/pay",
            "https://evil.com/?next=https://pgw.ceca.es",
            "https://evil.example/?next=https://pgw.ceca.es",
            "https://foo.pgw.ceca.es/pay",
            "https://criosrango.es.evil.example/pay",
            "https://criosrango.es./pay"
        ).forEach { assertFalse(PaymentUrlValidator.isAllowed(it), "Expected rejected: $it") }
    }

    @Test
    fun rejectsUserInfoEmptyHostMalformedUrlsAndNonDefaultPorts() {
        listOf(
            "https://pgw.ceca.es@evil.com/pay",
            "https://pgw.ceca.es@evil.example/pay",
            "https://user:password@pgw.ceca.es/pay",
            "https:///path",
            "https://",
            "https://pgw.ceca.es:8443/pay",
            "https://pgw.ceca.es:80/pay",
            "https://[::1",
            "https://pgw.ceca.es/contains space"
        ).forEach { assertFalse(PaymentUrlValidator.isAllowed(it), "Expected rejected: $it") }
    }
}
