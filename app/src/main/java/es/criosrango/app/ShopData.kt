package es.criosrango.app

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import androidx.core.text.HtmlCompat
import java.text.Normalizer
import java.util.Locale

const val STORE_API_BASE_URL = "https://criosrango.es/wp-json/wc/store/v1/"

data class StoreProduct(
    val id: Int = 0,
    val name: String = "",
    val permalink: String = "",
    val type: String = "simple",
    @SerializedName("short_description") val shortDescription: String = "",
    val description: String = "",
    @SerializedName("on_sale") val onSale: Boolean = false,
    val prices: ProductPrices = ProductPrices(),
    val images: List<ProductImage> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    @SerializedName("extensions") val extensions: StoreProductExtensions? = null,
    val tags: List<ProductTag> = emptyList(),
    val attributes: List<ProductAttribute> = emptyList(),
    val variations: List<ProductVariation> = emptyList(),
    @SerializedName("is_in_stock") val isInStock: Boolean = true,
    @SerializedName("is_purchasable") val isPurchasable: Boolean? = null,
    @SerializedName("is_on_backorder") val isOnBackorder: Boolean? = null,
    @SerializedName("low_stock_remaining") val lowStockRemaining: Int? = null,
    @SerializedName("stock_status") val stockStatus: String? = null,
    @SerializedName("stock_quantity") val stockQuantity: Int? = null,
    @SerializedName("manage_stock") val manageStock: Boolean? = null,
    @SerializedName("quantity_limits") val quantityLimits: QuantityLimits? = null,
    @SerializedName("stock_availability") val stockAvailability: StockAvailability? = null,
    @SerializedName("add_to_cart") val addToCart: AddToCart? = null
) {
    val originalCategoryIds: List<Int>
        get() = extensions?.criosrangoOutlet?.originalCategoryIds.orEmpty()
}

data class StoreProductExtensions(
    @SerializedName("criosrango_outlet") val criosrangoOutlet: OutletOriginExtension? = null
)

data class OutletOriginExtension(
    @SerializedName("original_category_ids") val originalCategoryIds: List<Int> = emptyList()
)

data class StockAvailability(
    val text: String = "",
    @SerializedName("class") val className: String = ""
)

data class AddToCart(
    val minimum: Int? = null,
    val maximum: Int? = null,
    @SerializedName("multiple_of") val multipleOf: Int? = null
)

data class QuantityLimits(
    val minimum: Int? = null,
    val maximum: Int? = null,
    @SerializedName("multiple_of") val multipleOf: Int? = null
)

data class PurchaseLimits(val minimum: Int, val maximum: Int, val multipleOf: Int)

data class ProductPrices(
    val price: String = "0",
    @SerializedName("regular_price") val regularPrice: String = "0",
    @SerializedName("sale_price") val salePrice: String = "0",
    @SerializedName("currency_symbol") val currencySymbol: String = "€",
    @SerializedName("currency_minor_unit") val currencyMinorUnit: Int = 2
)

data class ProductImage(
    val src: String = "",
    val thumbnail: String = "",
    val alt: String = ""
)

data class ProductAttribute(
    val name: String = "",
    val taxonomy: String? = null,
    val terms: List<AttributeTerm> = emptyList()
)

data class AttributeTerm(
    val name: String = "",
    val slug: String = "",
    val default: Boolean = false
)

data class ProductVariation(
    val id: Int = 0,
    val attributes: List<VariationAttribute> = emptyList(),
    val prices: ProductPrices = ProductPrices(),
    val images: List<ProductImage> = emptyList(),
    @SerializedName("is_in_stock") val isInStock: Boolean? = null,
    @SerializedName("is_purchasable") val isPurchasable: Boolean? = null,
    @SerializedName("is_on_backorder") val isOnBackorder: Boolean? = null,
    @SerializedName("low_stock_remaining") val lowStockRemaining: Int? = null,
    @SerializedName("stock_status") val stockStatus: String? = null,
    @SerializedName("stock_quantity") val stockQuantity: Int? = null,
    @SerializedName("manage_stock") val manageStock: Boolean? = null,
    @SerializedName("quantity_limits") val quantityLimits: QuantityLimits? = null,
    @SerializedName("add_to_cart") val addToCart: AddToCart? = null
)

data class VariationAttribute(
    val name: String = "",
    val value: String = ""
)

data class ProductTag(
    val id: Int = 0,
    val name: String = "",
    val slug: String = ""
)

data class ProductCategory(
    val id: Int = 0,
    val parent: Int = 0,
    val name: String = "",
    val slug: String = "",
    val count: Int = 0,
    val image: ProductImage? = null
)

data class CartItem(
    val lineKey: String = "",
    val productId: Int,
    val name: String,
    val imageUrl: String,
    val unitPrice: String,
    val variationId: Int? = null,
    val selectedAttributes: Map<String, String> = emptyMap(),
    val variationLabel: String = "",
    val quantity: Int = 1
)

data class CartVariation(
    val attribute: String,
    val value: String
)

data class AddCartRequest(
    val id: Int,
    val quantity: Int,
    val variation: List<CartVariation> = emptyList()
)

data class CartLine(
    val key: String = "",
    val id: Int = 0,
    val parentProductId: Int? = null,
    val name: String = "",
    val quantity: Int = 0,
    @SerializedName("quantity_limits") val quantityLimits: QuantityLimits? = null,
    val prices: ProductPrices = ProductPrices(),
    val totals: CartLineTotals = CartLineTotals(),
    val images: List<ProductImage> = emptyList(),
    val variation: List<CartVariation> = emptyList(),
    @Transient val consumerUnitPrice: String? = null
)

data class CartLineTotals(
    @SerializedName("line_price")
    val linePrice: String = "0",
    @SerializedName("line_price_tax")
    val linePriceTax: String = "0",
    @SerializedName("line_subtotal")
    val lineSubtotal: String = "0",
    @SerializedName("line_subtotal_tax")
    val lineSubtotalTax: String = "0",
    @SerializedName("line_total")
    val lineTotal: String = "0",
    @SerializedName("line_total_tax")
    val lineTotalTax: String = "0",
    @SerializedName("discount")
    val discount: String = "0",
    @SerializedName("discount_tax")
    val discountTax: String = "0"
)

fun CartLine.consumerUnitPrice(): String = consumerUnitPrice ?: totals.consumerSubtotal()
    .toBigDecimalOrZero()
    .divide(quantity.coerceAtLeast(1).toBigDecimal(), 0, java.math.RoundingMode.HALF_UP)
    .toPlainString()

fun CartLineTotals.consumerSubtotal(): String = lineTotal.toBigDecimalOrZero()
    .add(lineTotalTax.toBigDecimalOrZero())
    .toPlainString()

internal fun String.toBigDecimalOrZero(): java.math.BigDecimal = toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO

data class CartTotals(
    @SerializedName("total_items")
    val totalItems: String = "0",
    @SerializedName("total_items_tax")
    val totalItemsTax: String = "0",
    @SerializedName("total_fees")
    val totalFees: String = "0",
    @SerializedName("total_fees_tax")
    val totalFeesTax: String = "0",
    @SerializedName("total_discount")
    val totalDiscount: String = "0",
    @SerializedName("total_discount_tax")
    val totalDiscountTax: String = "0",
    @SerializedName("total_shipping")
    val totalShipping: String? = null,
    @SerializedName("total_shipping_tax")
    val totalShippingTax: String? = null,
    @SerializedName("total_price")
    val totalPrice: String = "0",
    @SerializedName("total_tax")
    val totalTax: String = "0",
    val currencySymbol: String = "€",
    val currencyMinorUnit: Int = 2
)

fun CartTotals.consumerSubtotal(): String = totalItems.toBigDecimalOrZero()
    .add(totalItemsTax.toBigDecimalOrZero())
    .toPlainString()

fun CartTotals.consumerShipping(): String = (totalShipping ?: "0").toBigDecimalOrZero()
    .add((totalShippingTax ?: "0").toBigDecimalOrZero())
    .toPlainString()

data class WooCart(
    val items: List<CartLine> = emptyList(),
    val coupons: List<CartCoupon> = emptyList(),
    val totals: CartTotals = CartTotals(),
    @SerializedName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerializedName("shipping_rates") val shippingRates: List<ShippingRate> = emptyList(),
    @SerializedName("shipping_packages") val shippingPackages: JsonElement? = null,
    @SerializedName("items_count") val itemsCount: Int = 0,
    val errors: List<CartError> = emptyList()
)

data class ShippingRate(
    @SerializedName("package_id") val packageId: Int = 0,
    val name: String = "",
    val destination: ShippingDestination? = null,
    @SerializedName("shipping_rates") val rates: List<ShippingOption> = emptyList()
)

data class ShippingDestination(
    @SerializedName("address_1") val address1: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = ""
)

data class ShippingOption(
    @SerializedName("rate_id") val rateId: String = "",
    val name: String = "",
    @SerializedName("method_id") val methodId: String = "",
    val price: String = "0",
    val taxes: String = "0",
    @SerializedName("currency_symbol") val currencySymbol: String = "€",
    @SerializedName("currency_minor_unit") val currencyMinorUnit: Int = 2,
    val selected: Boolean = false
)

data class SelectShippingRateRequest(
    @SerializedName("package_id") val packageId: Int,
    @SerializedName("rate_id") val rateId: String
)

data class CustomerAddress(
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val email: String,
    val phone: String,
    @SerializedName("address_1") val address1: String,
    val postcode: String,
    val city: String,
    val state: String,
    val country: String
) 

data class SpanishProvince(val code: String, val name: String)
data class SpanishMunicipality(val name: String, val postalCodes: List<String>)

val SPANISH_PROVINCES = listOf(
    SpanishProvince("C", "A Coruña"), SpanishProvince("VI", "Álava"), SpanishProvince("AB", "Albacete"),
    SpanishProvince("A", "Alicante"), SpanishProvince("AL", "Almería"), SpanishProvince("O", "Asturias"),
    SpanishProvince("AV", "Ávila"), SpanishProvince("BA", "Badajoz"), SpanishProvince("B", "Barcelona"),
    SpanishProvince("BI", "Bizkaia"), SpanishProvince("BU", "Burgos"), SpanishProvince("CC", "Cáceres"),
    SpanishProvince("CA", "Cádiz"), SpanishProvince("S", "Cantabria"), SpanishProvince("CS", "Castellón"),
    SpanishProvince("CE", "Ceuta"), SpanishProvince("CR", "Ciudad Real"), SpanishProvince("CO", "Córdoba"),
    SpanishProvince("CU", "Cuenca"), SpanishProvince("GI", "Girona"), SpanishProvince("GR", "Granada"),
    SpanishProvince("GU", "Guadalajara"), SpanishProvince("SS", "Gipuzkoa"), SpanishProvince("H", "Huelva"),
    SpanishProvince("HU", "Huesca"), SpanishProvince("J", "Jaén"), SpanishProvince("LE", "León"),
    SpanishProvince("L", "Lleida"), SpanishProvince("LO", "La Rioja"), SpanishProvince("LU", "Lugo"),
    SpanishProvince("M", "Madrid"), SpanishProvince("MA", "Málaga"), SpanishProvince("ML", "Melilla"),
    SpanishProvince("MU", "Murcia"), SpanishProvince("NA", "Navarra"), SpanishProvince("OR", "Ourense"),
    SpanishProvince("P", "Palencia"), SpanishProvince("GC", "Las Palmas"), SpanishProvince("PO", "Pontevedra"),
    SpanishProvince("SA", "Salamanca"), SpanishProvince("TF", "Santa Cruz de Tenerife"), SpanishProvince("SG", "Segovia"),
    SpanishProvince("SE", "Sevilla"), SpanishProvince("SO", "Soria"), SpanishProvince("T", "Tarragona"),
    SpanishProvince("TE", "Teruel"), SpanishProvince("TO", "Toledo"), SpanishProvince("V", "Valencia"),
    SpanishProvince("VA", "Valladolid"), SpanishProvince("ZA", "Zamora"),
    SpanishProvince("Z", "Zaragoza")
)

val SPANISH_MUNICIPALITIES: Map<String, List<SpanishMunicipality>> = mapOf(
    "M" to listOf(
        SpanishMunicipality("Madrid", listOf("28001", "28002", "28003", "28004", "28005", "28006", "28007", "28008", "28009", "28010")),
        SpanishMunicipality("Arganda del Rey", listOf("28500")),
        SpanishMunicipality("Alcalá de Henares", listOf("28801", "28802")),
        SpanishMunicipality("Getafe", listOf("28901", "28902", "28903")),
        SpanishMunicipality("Leganés", listOf("28911", "28912", "28913")),
        SpanishMunicipality("Rivas-Vaciamadrid", listOf("28521", "28522")),
        SpanishMunicipality("Móstoles", listOf("28931", "28932", "28933"))
    ),
    "CU" to listOf(
        SpanishMunicipality("Tarancón", listOf("16400")),
        SpanishMunicipality("Cuenca", listOf("16001", "16002", "16003")),
        SpanishMunicipality("Villanueva de la Jara", listOf("16420")),
        SpanishMunicipality("San Clemente", listOf("16410"))
    ),
    "TO" to listOf(
        SpanishMunicipality("Toledo", listOf("45001", "45002", "45003", "45004")),
        SpanishMunicipality("Talavera de la Reina", listOf("45600", "45601")),
        SpanishMunicipality("Illescas", listOf("45200")),
        SpanishMunicipality("Mora", listOf("45120"))
    ),
    "B" to listOf(
        SpanishMunicipality("Barcelona", listOf("08001", "08002", "08003", "08004", "08005", "08006")),
        SpanishMunicipality("Badalona", listOf("08911", "08912")),
        SpanishMunicipality("Girona", listOf("17001", "17002")),
        SpanishMunicipality("Lleida", listOf("25001", "25002"))
    ),
    "V" to listOf(
        SpanishMunicipality("Valencia", listOf("46001", "46002", "46003", "46004", "46005")),
        SpanishMunicipality("Torrent", listOf("46900", "46901")),
        SpanishMunicipality("Paterna", listOf("46980")),
        SpanishMunicipality("Alicante", listOf("03001", "03002"))
    ),
    "A" to listOf(
        SpanishMunicipality("Alicante", listOf("03001", "03002", "03003")),
        SpanishMunicipality("Elche", listOf("03202", "03203")),
        SpanishMunicipality("Benidorm", listOf("03500", "03501")),
        SpanishMunicipality("Orihuela", listOf("03300", "03301"))
    ),
    "AL" to listOf(
        SpanishMunicipality("Almería", listOf("04001", "04002", "04003")),
        SpanishMunicipality("Roquetas de Mar", listOf("04740", "04741")),
        SpanishMunicipality("Vera", listOf("04600")),
        SpanishMunicipality("El Ejido", listOf("04700"))
    ),
    "SE" to listOf(
        SpanishMunicipality("Sevilla", listOf("41001", "41002", "41003", "41004")),
        SpanishMunicipality("Dos Hermanas", listOf("41700", "41701")),
        SpanishMunicipality("Mairena del Aljarafe", listOf("41927")),
        SpanishMunicipality("Carmona", listOf("41410"))
    ),
    "Z" to listOf(
        SpanishMunicipality("Zaragoza", listOf("50001", "50002", "50003", "50004")),
        SpanishMunicipality("Huesca", listOf("22001", "22002")),
        SpanishMunicipality("Teruel", listOf("44001", "44002")),
        SpanishMunicipality("Calatayud", listOf("50300"))
    )
)

fun municipalitiesForProvince(provinceCode: String?): List<SpanishMunicipality> =
    SPANISH_MUNICIPALITIES[provinceCode.orEmpty()] ?: emptyList()

fun postalCodesForMunicipality(
    provinceCode: String?,
    municipalityName: String
): List<String> = emptyList()

fun isMunicipalityCompatibleWithProvince(provinceCode: String?, municipalityName: String): Boolean {
    if (municipalityName.trim().isEmpty()) return false
    val knownProvince = SPANISH_MUNICIPALITIES.entries
        .firstOrNull { (_, municipalities) -> municipalities.any { it.name.normalizedKey() == municipalityName.normalizedKey() } }
        ?.key
    // Unknown municipalities remain valid manual entries; known ones cannot be
    // combined with a different province (notably Madrid/Cuenca and Tarancón).
    return knownProvince == null || knownProvince == provinceCode
}

fun isPostalCodeCompatibleWithMunicipality(provinceCode: String?, municipalityName: String, postalCode: String): Boolean =
    postalCodesForMunicipality(provinceCode, municipalityName).let {
    return postalCode.length == 5 && postalCode.all(Char::isDigit)
}

private val PROVINCE_POSTAL_PREFIXES = mapOf(
    "M" to "28", "CU" to "16", "TO" to "45", "B" to "08", "V" to "46", "A" to "03",
    "AL" to "04", "CA" to "11", "S" to "39", "CS" to "12", "CR" to "13", "CO" to "14",
    "GR" to "18", "GU" to "19", "H" to "21", "HU" to "22", "J" to "23", "LE" to "24",
    "L" to "25", "LO" to "26", "LU" to "27", "MA" to "29", "MU" to "30", "NA" to "31",
    "OR" to "32", "P" to "34", "GC" to "35", "PO" to "36", "SA" to "37", "TF" to "38",
    "SG" to "40", "SE" to "41", "SO" to "42", "T" to "43", "TE" to "44", "VA" to "47",
    "ZA" to "49", "Z" to "50", "GI" to "17"
)

fun isPostalCodeCompatibleWithProvince(provinceCode: String?, postalCode: String): Boolean {
    return postalCode.length == 5 && postalCode.all(Char::isDigit)
}

data class UpdateCustomerRequest(
    @SerializedName("billing_address") val billingAddress: CustomerAddress,
    @SerializedName("shipping_address") val shippingAddress: CustomerAddress = billingAddress
)

data class OrderStatusResponse(
    @SerializedName("order_id") val id: Int = 0,
    @SerializedName("status") val status: String = "",
    @SerializedName("paid") val paid: Boolean = false,
    @SerializedName("needs_payment") val needsPayment: Boolean = true,
    @SerializedName("terminal") val terminal: Boolean = false
)

data class CheckoutResponse(
    @SerializedName("order_id") val orderId: Int? = null,
    @SerializedName("order_key") val orderKey: String? = null,
    @SerializedName("order_number") val orderNumber: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerializedName("payment_requirements") val paymentRequirements: List<String> = emptyList(),
    @SerializedName("redirect_url") val redirectUrl: String? = null,
    @SerializedName("payment_result") val paymentResult: PaymentResult? = null,
    @SerializedName("__experimentalCart") val experimentalCart: ExperimentalCart? = null,
    val totals: CartTotals = CartTotals(),
    val errors: List<CartError> = emptyList()
)

data class PaymentResult(
    @SerializedName("payment_status") val paymentStatus: String? = null,
    @SerializedName("redirect_url") val redirectUrl: String? = null,
    @SerializedName("payment_url") val paymentUrl: String? = null,
    @SerializedName("payment_details") val paymentDetails: List<PaymentDetail> = emptyList()
)

data class PaymentDetail(
    val key: String = "",
    val value: String = ""
)

data class ExperimentalCart(
    @SerializedName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerializedName("payment_requirements") val paymentRequirements: List<String> = emptyList(),
    val totals: CartTotals = CartTotals()
)

data class CreateOrderRequest(
    @SerializedName("payment_method") val paymentMethod: String,
    val billing_address: CustomerAddress,
    val shipping_address: CustomerAddress = billing_address,
    @SerializedName("shipping_rate") val shippingRate: String? = null,
    @SerializedName("expected_total") val expectedTotal: String? = null,
    @SerializedName("payment_data") val paymentData: Map<String, String> = emptyMap(),
    @SerializedName("customer_note") val customerNote: String? = null
)

/** Gateways are a property of the current checkout quote, never of a stale cart. */
fun CheckoutResponse.availablePaymentMethods(): List<String> =
    (paymentMethods + experimentalCart?.paymentMethods.orEmpty())
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot { it == "cod" }
        .distinct()

fun CheckoutResponse.availablePaymentRequirements(): List<String> =
    paymentRequirements.ifEmpty { experimentalCart?.paymentRequirements.orEmpty() }

fun CheckoutResponse.displayPaymentMethodLabels(): List<String> =
    availablePaymentMethods().map { raw -> raw.toPaymentMethodLabel() }

fun String.toPaymentMethodLabel(): String = when (this) {
    "cecabank_gateway" -> "Pago con tarjeta"
    "bizum" -> "Bizum"
    "cheque" -> "Bizum"
    "cod" -> "Contra reembolso"
    "bacs" -> "Transferencia bancaria"
    "redsys" -> "Pago con tarjeta"
    else -> this.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

/** Resolves the documented Store API fields and the URL-shaped details used by gateways. */
fun CheckoutResponse.paymentRedirectUrl(): String? {
    val details = paymentResult?.paymentDetails.orEmpty()
        .filter { it.key.lowercase(Locale.ROOT) in setOf("redirect_url", "redirect", "payment_url", "url") }
        .map { it.value }
    return (listOfNotNull(redirectUrl, paymentResult?.redirectUrl, paymentResult?.paymentUrl) + details)
        .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
}

data class CartCoupon(
    val code: String = "",
    val label: String = "",
    val totals: CartCouponTotals = CartCouponTotals()
)

data class CartCouponTotals(
    @SerializedName("total_discount")
    val totalDiscount: String = "0",
    @SerializedName("total_discount_tax")
    val totalDiscountTax: String = "0"
)

data class CartError(
    val code: String = "",
    val message: String = ""
)

fun StoreProduct.displayPrice(): String {
    return formatMinorUnits(prices.price, prices.currencyMinorUnit, prices.currencySymbol)
}

fun StoreProduct.regularDisplayPrice(): String {
    return formatMinorUnits(prices.regularPrice, prices.currencyMinorUnit, prices.currencySymbol)
}

fun formatMinorUnits(value: String, minorUnit: Int = 2, symbol: String = "€"): String {
    val amount = value.toLongOrNull()?.toBigDecimal()?.movePointLeft(minorUnit) ?: java.math.BigDecimal.ZERO
    return "${amount.setScale(minorUnit).toPlainString().replace('.', ',')} $symbol"
}

fun String.cleanWooText(): String = HtmlCompat.fromHtml(decodeUnicodeEscapes(), HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()

/** Some gateway errors arrive double-escaped (for example `\\u00e1`). */
fun String.decodeUnicodeEscapes(): String = Regex("\\\\u([0-9a-fA-F]{4})").replace(this) { match ->
    match.groupValues[1].toInt(16).toChar().toString()
}

fun normalizeAttributeValue(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace("\\p{M}".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace("&", "and")
    .replace("[^a-z0-9]".toRegex(), "")

fun String.normalizedKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace("\\p{M}".toRegex(), "")
    .lowercase(Locale.ROOT)
    .trim()

fun attributesMatch(first: String, second: String): Boolean {
    val firstNormalized = normalizeAttributeValue(first).removePrefix("pa")
    val secondNormalized = normalizeAttributeValue(second).removePrefix("pa")
    return firstNormalized == secondNormalized
}

fun attributeValuesMatch(first: String, second: String): Boolean =
    normalizeAttributeValue(first) == normalizeAttributeValue(second)

fun StoreProduct.purchaseLimits(): PurchaseLimits? {
    val minimum = quantityLimits?.minimum ?: addToCart?.minimum ?: return null
    val rawMaximum = quantityLimits?.maximum ?: addToCart?.maximum
    val multipleOf = quantityLimits?.multipleOf ?: addToCart?.multipleOf ?: return null
    val maximum = rawMaximum?.takeUnless { it == 9999 }
    if (minimum < 1 || maximum != null && maximum < minimum || multipleOf < 1) return null
    return PurchaseLimits(minimum, maximum ?: Int.MAX_VALUE, multipleOf)
}

fun ProductVariation.isAvailableForPurchase(): Boolean = isInStock == true && isPurchasable == true

fun ShippingOption.isTarancónLocalMethod(): Boolean {
    val haystack = listOf(name, methodId, rateId).joinToString(" ").normalizedKey()
    val localKeywords = listOf("local", "pickup", "recogida", "delivery", "entrega", "takeaway", "tienda")
    return haystack.contains("tarancon") && localKeywords.any { haystack.contains(it) }
}

fun ShippingOption.displayShippingName(): String {
    return name.cleanWooText()
}

fun isTarancónDestination(destination: ShippingDestination?): Boolean {
    if (destination == null) return false
    val city = destination.city.normalizedKey()
    val postcode = destination.postcode.trim()
    val province = destination.state.normalizedKey()
    val hasTaranconCity = city.contains("tarancon")
    val hasTaranconPostcode = postcode.startsWith("1640")
    val hasTaranconProvince = province.contains("cuenca")
    return hasTaranconCity && (hasTaranconPostcode || hasTaranconProvince)
}

fun WooCart.visibleShippingRatesForDestination(): List<ShippingRate> = shippingRates.map { packageRate ->
    val destination = packageRate.destination
    val baseFilteredRates = packageRate.rates.filterNot { rate ->
        rate.isTarancónLocalMethod() && !isTarancónDestination(destination)
    }
    val filteredRates = baseFilteredRates.filterNot { rate ->
        val normalizedName = rate.name.normalizedKey()
        val rawPrice = rate.price.toBigDecimalOrZero()
        val hasFreeShipping = baseFilteredRates.any { it.rateId != rate.rateId && it.price.toBigDecimalOrZero() == java.math.BigDecimal.ZERO }
        hasFreeShipping && rawPrice > java.math.BigDecimal.ZERO && (normalizedName.contains("gratis") || normalizedName.contains("free") || normalizedName.contains("correos express"))
    }
    packageRate.copy(rates = filteredRates)
}

fun WooCart.hasVisibleShipping(): Boolean = visibleShippingRatesForDestination().any { it.rates.isNotEmpty() }
