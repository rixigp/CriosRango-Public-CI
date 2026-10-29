package es.criosrango.app

import es.criosrango.shared.api.StoreApiException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreErrorTest {
    @Test
    fun timeoutMapsToFriendlyMessage() {
        val message = SocketTimeoutException("Request timeout has expired").toStoreCatalogErrorMessage()
        assertTrue(message.contains("La conexión está tardando demasiado"))
        assertTrue(message.contains("Inténtalo de nuevo"))
        assertFalse(message.contains("Request timeout has expired"))
    }

    @Test
    fun connectionFailureMapsToFriendlyMessage() {
        val message = IOException("connect timed out at https://internal.example").toStoreCatalogErrorMessage()
        assertTrue(message.contains("Sin conexión a Internet"))
        assertTrue(message.contains("Comprueba tu conexión"))
        assertFalse(message.contains("https://internal.example"))
        assertFalse(message.contains("connect timed out"))
    }

    @Test
    fun serverFailureMapsToFriendlyMessage() {
        val message = StoreApiException(503, "backend_internal", "upstream failed at /private").toStoreCatalogErrorMessage()
        assertTrue(message.contains("No podemos conectar con la tienda"))
        assertTrue(message.contains("no está disponible"))
        assertFalse(message.contains("backend_internal"))
        assertFalse(message.contains("/private"))
    }

    @Test
    fun unknownFailureUsesSafeFallback() {
        val message = IllegalStateException("IllegalStateException: SQL host=db.internal").toStoreCatalogErrorMessage()
        assertTrue(message.contains("Ha ocurrido un problema"))
        assertTrue(message.contains("Inténtalo de nuevo"))
        assertFalse(message.contains("SQL host=db.internal"))
        assertFalse(message.contains("IllegalStateException"))
    }

    @Test
    fun technicalThrowableMessageIsNeverReturnedDirectly() {
        val raw = "Request timeout has expired"
        val rendered = IOException(raw).toStoreCatalogErrorMessage()
        assertFalse(rendered == raw)
        assertFalse(rendered.contains(raw))
    }
}
