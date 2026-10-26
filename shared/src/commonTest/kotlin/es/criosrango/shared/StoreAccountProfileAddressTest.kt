package es.criosrango.shared

import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import es.criosrango.shared.account.AccountUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreAccountProfileAddressTest {
    @Test
    fun profileRequiresBothNamesAndTrimsValues() {
        assertEquals("Introduce tu nombre y apellidos.", StoreAccountFormValidation.profileError("", "Pérez"))
        assertEquals("Introduce tu nombre y apellidos.", StoreAccountFormValidation.profileError("Ana", "  "))
        assertNull(StoreAccountFormValidation.profileError(" Ana ", " Pérez "))
        assertEquals("Ana", StoreAccountFormValidation.mergeProfileNames(AccountCustomerAddress(), " Ana ", " Pérez ").firstName)
        assertEquals("Pérez", StoreAccountFormValidation.mergeProfileNames(AccountCustomerAddress(), " Ana ", " Pérez ").lastName)
    }

    @Test
    fun profileNameMergePreservesEveryNonProfileAddressField() {
        val original = AccountCustomerAddress(
            firstName = "Viejo",
            lastName = "Nombre",
            email = "ana@example.com",
            phone = "+34600111222",
            address1 = "Calle Uno 12",
            address2 = "3º B",
            postcode = "28013",
            city = "Madrid",
            state = "M",
            country = "ES"
        )
        val updated = StoreAccountFormValidation.mergeProfileNames(original, " Ana ", " Pérez ")
        assertEquals("Ana", updated.firstName)
        assertEquals("Pérez", updated.lastName)
        assertEquals(original.email, updated.email)
        assertEquals(original.phone, updated.phone)
        assertEquals(original.address1, updated.address1)
        assertEquals(original.address2, updated.address2)
        assertEquals(original.postcode, updated.postcode)
        assertEquals(original.city, updated.city)
        assertEquals(original.state, updated.state)
        assertEquals(original.country, updated.country)
    }

    @Test
    fun addressValidationTrimsFieldsForSavingAndForbidsNonSpanishPostcodes() {
        val valid = AccountCustomerAddress(
            firstName = " Ana ", lastName = " Pérez ", address1 = " Calle Uno ",
            address2 = " 3º B ", postcode = "28013", city = " Madrid ",
            state = "m", country = "PT", email = "ana@example.com", phone = "+34600111222"
        )
        assertNull(StoreAccountFormValidation.addressError(valid))
        val clean = StoreAccountFormValidation.cleanAddress(valid)
        assertEquals("Ana", clean.firstName)
        assertEquals("Pérez", clean.lastName)
        assertEquals("Calle Uno", clean.address1)
        assertEquals("3º B", clean.address2)
        assertEquals("Madrid", clean.city)
        assertEquals("M", clean.state)
        assertEquals("ES", clean.country)
        assertEquals("ana@example.com", clean.email)
        assertEquals("+34600111222", clean.phone)
        assertEquals("Introduce un código postal válido.", StoreAccountFormValidation.addressError(valid.copy(postcode = "2801A")))
        assertEquals("Introduce un código postal válido.", StoreAccountFormValidation.addressError(valid.copy(postcode = "2801")))
    }

    @Test
    fun addressRequiresMandatoryFieldsAndKnownProvinceWithoutCheckoutGeographyRules() {
        val valid = AccountCustomerAddress(
            firstName = "Ana", lastName = "Pérez", address1 = "Calle Uno",
            postcode = "28013", city = "Madrid", state = "M"
        )
        assertNull(StoreAccountFormValidation.addressError(valid))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(address1 = " ")))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(firstName = " ")))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(lastName = " ")))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(postcode = " ")))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(city = " ")))
        assertEquals("Completa todos los campos obligatorios.", StoreAccountFormValidation.addressError(valid.copy(state = " ")))
        assertEquals("Selecciona una provincia.", StoreAccountFormValidation.addressError(valid.copy(state = "XX")))
        // Account validation intentionally does not apply checkout's province/postcode locality mapping.
        assertNull(StoreAccountFormValidation.addressError(valid.copy(postcode = "08001", city = "Madrid", state = "M")))
    }
    @Test
    fun updatedProfileUserIsPublishedToSharedAccountRootState() {
        val repository = AccountRepository(object : AccountTokenStore {
            private var token: String? = "valid-session-token"
            override fun load(): String? = token
            override fun save(token: String): Boolean { this.token = token; return true }
            override fun clear() { token = null }
        })
        val session = StoreAccountSessionState(repository)
        val updated = AccountUser(
            id = 7, email = "ana@example.com",
            firstName = "Ana", lastName = "Pérez", displayName = "Ana Pérez"
        )

        session.publishAuthenticatedUser(updated)

        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, session.snapshot.value.status)
        assertEquals(updated, session.snapshot.value.user)
        assertEquals(false, session.snapshot.value.loading)
    }

}
