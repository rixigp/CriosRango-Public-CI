package es.criosrango.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoreCart(
    val items: List<StoreCartLine> = emptyList(),
    val coupons: List<StoreCartCoupon> = emptyList(),
    val totals: StoreCartTotals = StoreCartTotals(),
    @SerialName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerialName("shipping_rates") val shippingRates: List<StoreShippingPackage> = emptyList(),
    @SerialName("items_count") val itemsCount: Int = 0,
    val errors: List<StoreCartError> = emptyList()
)

@Serializable
data class StoreCartLine(
    val key: String = "",
    val id: Int = 0,
    val name: String = "",
    val quantity: Int = 0,
    @SerialName("quantity_limits") val quantityLimits: StoreQuantityLimits? = null,
    val prices: ProductPrices = ProductPrices(),
    val totals: StoreCartLineTotals = StoreCartLineTotals(),
    val images: List<ProductImage> = emptyList(),
    val variation: List<StoreCartVariation> = emptyList()
)

@Serializable
data class StoreCartLineTotals(
    @SerialName("line_price") val linePrice: String = "0",
    @SerialName("line_price_tax") val linePriceTax: String = "0",
    @SerialName("line_subtotal") val lineSubtotal: String = "0",
    @SerialName("line_subtotal_tax") val lineSubtotalTax: String = "0",
    @SerialName("line_total") val lineTotal: String = "0",
    @SerialName("line_total_tax") val lineTotalTax: String = "0",
    val discount: String = "0",
    @SerialName("discount_tax") val discountTax: String = "0"
)

@Serializable
data class StoreCartTotals(
    @SerialName("total_items") val totalItems: String = "0",
    @SerialName("total_items_tax") val totalItemsTax: String = "0",
    @SerialName("total_fees") val totalFees: String = "0",
    @SerialName("total_fees_tax") val totalFeesTax: String = "0",
    @SerialName("total_discount") val totalDiscount: String = "0",
    @SerialName("total_discount_tax") val totalDiscountTax: String = "0",
    @SerialName("total_shipping") val totalShipping: String? = null,
    @SerialName("total_shipping_tax") val totalShippingTax: String? = null,
    @SerialName("total_price") val totalPrice: String = "0",
    @SerialName("total_tax") val totalTax: String = "0",
    @SerialName("currency_symbol") val currencySymbol: String = "€",
    @SerialName("currency_minor_unit") val currencyMinorUnit: Int = 2
)

@Serializable
data class StoreCartCoupon(
    val code: String = "",
    val label: String = "",
    val totals: StoreCartCouponTotals = StoreCartCouponTotals()
)

@Serializable
data class StoreCartCouponTotals(
    @SerialName("total_discount") val totalDiscount: String = "0",
    @SerialName("total_discount_tax") val totalDiscountTax: String = "0"
)

@Serializable
data class StoreCartVariation(
    val attribute: String = "",
    val value: String = ""
)

@Serializable
data class StoreShippingPackage(
    @SerialName("package_id") val packageId: Int = 0,
    val name: String = "",
    val destination: StoreShippingDestination? = null,
    @SerialName("shipping_rates") val rates: List<StoreShippingOption> = emptyList()
)

@Serializable
data class StoreShippingDestination(
    @SerialName("address_1") val address1: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = ""
)

@Serializable
data class StoreShippingOption(
    @SerialName("rate_id") val rateId: String = "",
    val name: String = "",
    @SerialName("method_id") val methodId: String = "",
    val price: String = "0",
    val taxes: String = "0",
    @SerialName("currency_symbol") val currencySymbol: String = "€",
    @SerialName("currency_minor_unit") val currencyMinorUnit: Int = 2,
    val selected: Boolean = false
)

@Serializable
data class StoreQuantityLimits(
    val minimum: Int? = null,
    val maximum: Int? = null,
    @SerialName("multiple_of") val multipleOf: Int? = null
)

@Serializable
data class StoreCartError(
    val code: String = "",
    val message: String = ""
)

@Serializable
data class StoreCartRequest(
    val id: Int,
    val quantity: Int,
    val variation: List<StoreCartVariation> = emptyList()
)

@Serializable
data class StoreCartApiError(
    val code: String = "",
    val message: String = "",
)
