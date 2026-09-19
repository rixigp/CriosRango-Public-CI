package es.criosrango.app

import android.util.Log
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.AddToCart as SharedAddToCart
import es.criosrango.shared.model.AttributeTerm as SharedAttributeTerm
import es.criosrango.shared.model.ProductAttribute as SharedProductAttribute
import es.criosrango.shared.model.ProductImage as SharedProductImage
import es.criosrango.shared.model.ProductPrices as SharedProductPrices
import es.criosrango.shared.model.ProductTag as SharedProductTag
import es.criosrango.shared.model.ProductVariation as SharedProductVariation
import es.criosrango.shared.model.QuantityLimits as SharedQuantityLimits
import es.criosrango.shared.model.StockAvailability as SharedStockAvailability
import es.criosrango.shared.model.StoreCategory as SharedStoreCategory
import es.criosrango.shared.model.StoreProduct as SharedStoreProduct
import es.criosrango.shared.model.VariationAttribute as SharedVariationAttribute
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import java.net.SocketTimeoutException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Temporary Phase C bridge between the stable Android StoreApi contract and
 * the validated shared KMP catalog client.
 *
 * Only the exact subset represented by StoreApiClient is routed to KMP.
 * Everything else deliberately delegates to the existing Retrofit StoreApi.
 */
class SharedCatalogStoreApiAdapter(
    private val retrofitApi: StoreApi,
    private val sharedClient: StoreApiClient
) : StoreApi by retrofitApi {

    override suspend fun product(id: Int): StoreProduct {
        Log.d("CriosRangoSharedCatalog", "PRODUCT_DETAIL source=shared id=$id")
        return try {
            sharedClient.product(id).toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun productWithVariationAvailability(id: Int): StoreProduct {
        Log.d("CriosRangoSharedCatalog", "PRODUCT_DETAIL_VARIATIONS source=shared id=$id")
        return try {
            sharedClient.productWithVariationAvailability(id).toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun products(
        perPage: Int,
        page: Int,
        search: String?,
        category: Int?,
        orderBy: String?,
        order: String?,
        after: String?,
        featured: Boolean?
    ): List<StoreProduct> {
        val isExactlySupported =
            search == null &&
                orderBy == null &&
                order == null &&
                after == null &&
                featured == null

        if (!isExactlySupported) {
            Log.d("CriosRangoSharedCatalog", "PRODUCTS source=retrofit unsupported-parameters")
            return retrofitApi.products(perPage, page, search, category, orderBy, order, after, featured)
        }

        Log.d(
            "CriosRangoSharedCatalog",
            "PRODUCTS source=shared StoreApiClient perPage=$perPage page=$page category=$category"
        )
        return try {
            sharedClient.products(
                perPage = perPage,
                page = page,
                search = search,
                category = category,
                orderBy = orderBy,
                order = order,
                after = after,
                featured = featured
            ).also {
                Log.d(
                    "CriosRangoSharedCatalog",
                    "PRODUCTS source=shared params=search=${search != null} category=$category orderby=$orderBy order=$order after=${after != null} featured=$featured"
                )
            }.map(SharedStoreProduct::toAndroid)
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun productsByTag(
        perPage: Int,
        page: Int,
        tag: String
    ): List<StoreProduct> {
        Log.d("CriosRangoSharedCatalog", "PRODUCTS source=shared params=tag=true perPage=$perPage page=$page")
        return try {
            sharedClient.products(perPage = perPage, page = page, tag = tag)
                .map(SharedStoreProduct::toAndroid)
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun categories(perPage: Int): List<ProductCategory> {
        Log.d("CriosRangoSharedCatalog", "CATEGORIES source=shared StoreApiClient perPage=$perPage")
        return try {
            sharedClient.categories(perPage = perPage).map(SharedStoreCategory::toAndroid)
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }
}

private fun SharedStoreProduct.toAndroid(): StoreProduct = StoreProduct(
    id = id,
    name = name,
    permalink = permalink,
    type = type,
    shortDescription = shortDescription,
    description = description,
    onSale = onSale,
    prices = prices.toAndroid(),
    images = images.map(SharedProductImage::toAndroid),
    categories = categories.map(SharedStoreCategory::toAndroid),
    tags = tags.map(SharedProductTag::toAndroid),
    attributes = attributes.map(SharedProductAttribute::toAndroid),
    variations = variations.map(SharedProductVariation::toAndroid),
    isInStock = isInStock,
    isPurchasable = isPurchasable,
    isOnBackorder = isOnBackorder,
    lowStockRemaining = lowStockRemaining,
    stockStatus = stockStatus,
    stockQuantity = stockQuantity,
    manageStock = manageStock,
    quantityLimits = quantityLimits?.toAndroid(),
    stockAvailability = stockAvailability?.toAndroid(),
    addToCart = addToCart?.toAndroid()
)

private fun SharedProductImage.toAndroid(): ProductImage = ProductImage(
    src = src,
    thumbnail = thumbnail,
    alt = alt
)

private fun SharedStoreCategory.toAndroid(): ProductCategory = ProductCategory(
    id = id,
    parent = parent,
    name = name,
    slug = slug,
    count = count,
    image = image?.toAndroid()
)

private fun SharedProductPrices.toAndroid(): ProductPrices = ProductPrices(
    price = price,
    regularPrice = regularPrice,
    salePrice = salePrice,
    currencySymbol = currencySymbol,
    currencyMinorUnit = currencyMinorUnit
)

private fun SharedProductTag.toAndroid(): ProductTag = ProductTag(
    id = id,
    name = name,
    slug = slug
)

private fun SharedProductAttribute.toAndroid(): ProductAttribute = ProductAttribute(
    name = name,
    taxonomy = taxonomy,
    terms = terms.map(SharedAttributeTerm::toAndroid)
)

private fun SharedAttributeTerm.toAndroid(): AttributeTerm = AttributeTerm(
    name = name,
    slug = slug,
    default = default
)

private fun SharedProductVariation.toAndroid(): ProductVariation = ProductVariation(
    id = id,
    attributes = attributes.map(SharedVariationAttribute::toAndroid),
    prices = prices.toAndroid(),
    images = images.map(SharedProductImage::toAndroid),
    isInStock = isInStock,
    isPurchasable = isPurchasable,
    isOnBackorder = isOnBackorder,
    lowStockRemaining = lowStockRemaining,
    stockStatus = stockStatus,
    stockQuantity = stockQuantity,
    manageStock = manageStock,
    quantityLimits = quantityLimits?.toAndroid(),
    addToCart = addToCart?.toAndroid()
)

private fun SharedVariationAttribute.toAndroid(): VariationAttribute = VariationAttribute(
    name = name,
    value = value
)

private fun SharedQuantityLimits.toAndroid(): QuantityLimits = QuantityLimits(
    minimum = minimum,
    maximum = maximum,
    multipleOf = multipleOf
)

private fun SharedStockAvailability.toAndroid(): StockAvailability = StockAvailability(
    text = text,
    className = className
)

private fun SharedAddToCart.toAndroid(): AddToCart = AddToCart(
    minimum = minimum,
    maximum = maximum,
    multipleOf = multipleOf
)

private fun Exception.toAndroidCatalogException(): Exception = when (this) {
    is HttpRequestTimeoutException -> SocketTimeoutException(message).also { it.initCause(this) }
    is ResponseException -> {
        val code = response.status.value
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))
    }
    else -> this
}
