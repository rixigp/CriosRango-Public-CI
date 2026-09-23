package es.criosrango.shared

import androidx.compose.ui.graphics.Color

object AccountDeletion {
    const val URL = "https://www.criosrango.es/mi-cuenta/wpf-delete-account/"
    const val TITLE = "Eliminar cuenta"
    const val MESSAGE = "¿Quieres eliminar tu cuenta?\n\nEsta acción es irreversible. Se abrirá la página segura de Críos&Rango para confirmar la eliminación.\n\nAlgunos datos relacionados con pedidos o facturación podrán conservarse cuando exista una obligación legal."
    const val CANCEL = "Cancelar"
    const val CONTINUE = "Continuar"

    val background = Color(0xFFFFF4D6)
    val accent = Color(0xFF8A6200)
}
