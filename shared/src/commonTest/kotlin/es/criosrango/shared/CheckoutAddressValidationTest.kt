package es.criosrango.shared

import es.criosrango.shared.model.CustomerAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckoutAddressValidationTest {
    private fun valid() = CustomerAddress(firstName="Ana",lastName="García",email="ana@example.com",phone="600123123",address1="Calle Mayor 1",postcode="28001",city="Madrid",state="M",country="ES")
    @Test fun acceptsValidSpanishAddress() { assertNull(validateCheckoutAddress(valid())) }
    @Test fun requiredFieldsAndEmailAreValidated() {
        assertEquals("Indica tu nombre.",validateCheckoutAddress(valid().copy(firstName="")))
        assertEquals("Indica tus apellidos.",validateCheckoutAddress(valid().copy(lastName="")))
        assertEquals("Introduce un email válido.",validateCheckoutAddress(valid().copy(email="bad")))
        assertEquals("Indica un teléfono.",validateCheckoutAddress(valid().copy(phone="")))
        assertEquals("Indica una dirección.",validateCheckoutAddress(valid().copy(address1="")))
    }
    @Test fun provinceCityPostcodeAndCountryAreValidated() {
        assertEquals("Selecciona una provincia.",validateCheckoutAddress(valid().copy(state="")))
        assertEquals("La localidad no corresponde a la provincia seleccionada.",validateCheckoutAddress(valid().copy(state="CU")))
        assertEquals("Introduce un código postal de 5 cifras.",validateCheckoutAddress(valid().copy(postcode="2800")))
        assertEquals("El código postal no corresponde a la provincia seleccionada.",validateCheckoutAddress(valid().copy(postcode="16001")))
        assertEquals("Selecciona España.",validateCheckoutAddress(valid().copy(country="FR")))
    }
}
