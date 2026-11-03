package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalDocumentRepositoryTest {
    @Test fun blankPersistentCacheIsInvalid() {
        val blank = LegalPageCacheEntry("politica-de-privacidad", "Privacidad", "  ", 100L)
        assertFalse(isValidLegalCacheEntry(blank))
    }
    @Test fun blankRemoteDoesNotReplaceValidCachedEntry() {
        val good = LegalPageCacheEntry("politica-de-privacidad", "Privacidad", "<p>Contenido real</p>", 100L)
        val blankRemote = LegalPageCacheEntry("politica-de-privacidad", "Privacidad", "", 200L)
        assertEquals(good, chooseValidLegalEntry(blankRemote, good))
    }
    @Test fun validRemoteReplacesPreviousCache() {
        val old = LegalPageCacheEntry("slug", "Title", "<p>Old</p>", 100L)
        val fresh = LegalPageCacheEntry("slug", "Title", "<p>Fresh</p>", 200L)
        assertEquals(fresh, chooseValidLegalEntry(fresh, old))
        assertTrue(isValidLegalCacheEntry(fresh))
    }
}
