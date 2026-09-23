package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountDeletionTest {
    @Test
    fun deletionSpecIsStable() {
        assertEquals(
            "https://www.criosrango.es/mi-cuenta/wpf-delete-account/",
            AccountDeletion.URL
        )
        assertEquals("Eliminar cuenta", AccountDeletion.TITLE)
        assertEquals(
            "¿Quieres eliminar tu cuenta?\n\nEsta acción es irreversible. Se abrirá la página segura de Críos&Rango para confirmar la eliminación.\n\nAlgunos datos relacionados con pedidos o facturación podrán conservarse cuando exista una obligación legal.",
            AccountDeletion.MESSAGE
        )
        assertEquals("Cancelar", AccountDeletion.CANCEL)
        assertEquals("Continuar", AccountDeletion.CONTINUE)
    }
}
