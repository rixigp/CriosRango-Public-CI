package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductAppLinkParserTest {
    @Test fun parsesWwwProductUniversalLink() {
        assertEquals("conjunto-0200-yoedu", parseProductAppLinkUrl("https://www.criosrango.es/producto/conjunto-0200-yoedu/"))
    }
    @Test fun parsesApexProductUniversalLink() {
        assertEquals("conjunto-0200-yoedu", parseProductAppLinkUrl("https://criosrango.es/producto/conjunto-0200-yoedu/"))
    }
    @Test fun rejectsExternalHostsAndNonProductPaths() {
        assertNull(parseProductAppLinkUrl("https://evil.example/producto/conjunto-0200-yoedu/"))
        assertNull(parseProductAppLinkUrl("https://criosrango.es/app-payment-return?result=ok"))
        assertNull(parseProductAppLinkUrl("https://criosrango.es/categoria/conjunto-0200-yoedu/"))
    }
    @Test fun rejectsNonHttpsAndExtraPathSegments() {
        assertNull(parseProductAppLinkUrl("http://criosrango.es/producto/conjunto-0200-yoedu/"))
        assertNull(parseProductAppLinkUrl("https://criosrango.es/producto/conjunto-0200-yoedu/reviews/"))
    }
}
