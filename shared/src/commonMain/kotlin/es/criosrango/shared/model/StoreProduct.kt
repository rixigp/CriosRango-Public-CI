package es.criosrango.shared.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class StoreProduct(
    val id: Int = 0, val name: String = "", val permalink: String = "", val type: String = "simple",
    @SerialName("short_description") val shortDescription: String = "", val description: String = "",
    @SerialName("on_sale") val onSale: Boolean = false, val prices: ProductPrices = ProductPrices(),
    val images: List<ProductImage> = emptyList(), val categories: List<StoreCategory> = emptyList(),
    val extensions: StoreProductExtensions? = null,
    val tags: List<ProductTag> = emptyList(), val attributes: List<ProductAttribute> = emptyList(),
    val variations: List<ProductVariation> = emptyList(),
    @SerialName("is_in_stock") val isInStock: Boolean = true,
    @SerialName("is_purchasable") val isPurchasable: Boolean? = null,
    @SerialName("is_on_backorder") val isOnBackorder: Boolean? = null,
    @SerialName("low_stock_remaining") val lowStockRemaining: Int? = null,
    @SerialName("stock_status") val stockStatus: String? = null,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("manage_stock") val manageStock: Boolean? = null,
    @SerialName("quantity_limits") val quantityLimits: QuantityLimits? = null,
    @SerialName("stock_availability") val stockAvailability: StockAvailability? = null,
    @SerialName("add_to_cart") val addToCart: AddToCart? = null
)
@Serializable
data class StoreProductExtensions(
    @SerialName("criosrango_outlet") val criosrangoOutlet: OutletOriginExtension? = null
)
@Serializable
data class OutletOriginExtension(
    @SerialName("original_category_ids") val originalCategoryIds: List<Int> = emptyList()
)
@Serializable data class ProductPrices(
    val price: String = "0", @SerialName("regular_price") val regularPrice: String = "0",
    @SerialName("sale_price") val salePrice: String = "0",
    @SerialName("currency_symbol") val currencySymbol: String = "€",
    @SerialName("currency_minor_unit") val currencyMinorUnit: Int = 2
)
@Serializable data class StockAvailability(val text: String = "", @SerialName("class") val className: String = "")
@Serializable data class AddToCart(
    val minimum: Int? = null, val maximum: Int? = null, @SerialName("multiple_of") val multipleOf: Int? = null
)
@Serializable data class QuantityLimits(
    val minimum: Int? = null, val maximum: Int? = null, @SerialName("multiple_of") val multipleOf: Int? = null
)
@Serializable data class ProductVariation(
    val id: Int = 0, val attributes: List<VariationAttribute> = emptyList(),
    val prices: ProductPrices = ProductPrices(), val images: List<ProductImage> = emptyList(),
    @SerialName("is_in_stock") val isInStock: Boolean? = null,
    @SerialName("is_purchasable") val isPurchasable: Boolean? = null,
    @SerialName("is_on_backorder") val isOnBackorder: Boolean? = null,
    @SerialName("low_stock_remaining") val lowStockRemaining: Int? = null,
    @SerialName("stock_status") val stockStatus: String? = null,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("manage_stock") val manageStock: Boolean? = null,
    @SerialName("quantity_limits") val quantityLimits: QuantityLimits? = null,
    @SerialName("add_to_cart") val addToCart: AddToCart? = null
)
@Serializable data class VariationAttribute(val name: String = "", val value: String = "")
@Serializable data class ProductTag(val id: Int = 0, val name: String = "", val slug: String = "")
@Serializable data class ProductAttribute(
    val name: String = "", val taxonomy: String? = null, val terms: List<AttributeTerm> = emptyList()
)
@Serializable data class AttributeTerm(val name: String = "", val slug: String = "", val default: Boolean = false)