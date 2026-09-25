package es.criosrango.shared

import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreProduct
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreProductPriceTest {
    @Test
    fun formatsMinorUnits() {
        assertEquals("15,99 €", formatStorePrice("1599", 2, "€"))
        assertEquals("15,00 €", formatStorePrice("1500", 2, "€"))
        assertEquals("7,99 €", formatStorePrice("799", 2, "€"))
        assertEquals("0,00 €", formatStorePrice("0", 2, "€"))
    }

    @Test
    fun decodesWooCommercePrices() {
        val json = """{
          "id": 123,
          "name": "Producto de prueba",
          "prices": {
            "price": "1599",
            "regular_price": "1999",
            "sale_price": "1599",
            "currency_code": "EUR",
            "currency_symbol": "€",
            "currency_minor_unit": 2
          }
        }"""

        val product = Json { ignoreUnknownKeys = true }.decodeFromString<StoreProduct>(json)

        assertEquals("1599", product.prices.price)
        assertEquals("1999", product.prices.regularPrice)
        assertEquals(2, product.prices.currencyMinorUnit)
    }
}
