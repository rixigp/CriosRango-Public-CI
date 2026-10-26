package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreAccountHelpInformationTest {
    @Test
    fun helpContactsKeepAndroidGoldenLabelsValuesAndOrder() {
        assertEquals(
            listOf("WhatsApp" to "633 246 788", "Llamar" to "969 091 236", "Correo electrónico" to "criosrango@criosrango.es"),
            STORE_ACCOUNT_HELP_CONTACTS.map { it.title to it.value }
        )
        assertEquals(
            listOf(StoreAccountHelpContactKind.WHATSAPP, StoreAccountHelpContactKind.PHONE, StoreAccountHelpContactKind.EMAIL),
            STORE_ACCOUNT_HELP_CONTACTS.map { it.kind }
        )
    }

    @Test
    fun helpBusinessHoursKeepAndroidGoldenCopyAndOrder() {
        assertEquals(
            listOf(
                "Lunes" to "10:00–13:30 · 17:30–21:00",
                "Martes" to "10:00–13:30 · 17:30–21:00",
                "Miércoles" to "10:00–13:30 · 17:30–21:00",
                "Jueves" to "10:00–13:30 · 17:30–21:00",
                "Viernes" to "10:00–13:30 · 17:30–21:00",
                "Sábado" to "10:00–13:30",
                "Domingo" to "Cerrado"
            ),
            STORE_ACCOUNT_BUSINESS_HOURS.map { it.day to it.hours }
        )
    }
}
