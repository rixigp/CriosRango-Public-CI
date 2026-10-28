package es.criosrango.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProductAppLinkResolverTest {
    @Test fun validSlugUsesDirectResolverAndOpensProduct() = runTest {
        val opened = mutableListOf<String>(); var requestedSlug: String? = null; var errors = 0
        resolveProductAppLink(" /Conjunto-0200-Yoedu/ ", emptyList<String>(), { it },
            { slug -> requestedSlug = slug; "conjunto-0200-yoedu" }, opened::add, { errors++ })
        assertEquals("conjunto-0200-yoedu", requestedSlug)
        assertEquals(listOf("conjunto-0200-yoedu"), opened); assertEquals(0, errors)
    }
    @Test fun cachedProductOpensWithoutRequest() = runTest {
        val opened = mutableListOf<String>(); var requested = false
        resolveProductAppLink("conjunto-0200-yoedu", listOf("otro-producto", "conjunto-0200-yoedu"), { it },
            { requested = true; null }, opened::add, {})
        assertEquals(listOf("conjunto-0200-yoedu"), opened); assertFalse(requested)
    }
    @Test fun missingProductReportsControlledError() = runTest {
        var errors = 0
        resolveProductAppLink("no-existe", emptyList<String>(), { it }, { null }, {}, { errors++ })
        assertEquals(1, errors)
    }
    @Test fun networkFailureReportsControlledError() = runTest {
        var errors = 0
        resolveProductAppLink("conjunto-0200-yoedu", emptyList<String>(), { it },
            { throw java.io.IOException("offline") }, {}, { errors++ })
        assertEquals(1, errors)
    }
    @Test fun productUrlPathExtractsExpectedSlug() {
        assertEquals("conjunto-0200-yoedu", productSlugFromPath("/producto/conjunto-0200-yoedu/"))
        assertEquals("conjunto-0200-yoedu", productSlugFromPath("/PRODUCTO/Conjunto-0200-Yoedu/"))
        assertNull(productSlugFromPath("/categoria/conjunto-0200-yoedu/"))
    }
    @Test fun timeoutReportsControlledError() = runTest {
        var errors = 0
        resolveProductAppLink("conjunto-0200-yoedu", emptyList<String>(), { it },
            { kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.awaitCancellation<String>() } }, {}, { errors++ })
        assertEquals(1, errors)
    }
    @Test fun invalidSlugDoesNotRequestAndReportsError() = runTest {
        var requested = false; var errors = 0
        resolveProductAppLink(" / ", emptyList<String>(), { it }, { requested = true; null }, {}, { errors++ })
        assertFalse(requested); assertEquals(1, errors)
    }
}