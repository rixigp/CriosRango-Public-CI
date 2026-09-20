package es.criosrango.app

import android.util.Log
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreApiException
import es.criosrango.shared.model.StoreCart
import es.criosrango.shared.model.StoreCartRequest
import es.criosrango.shared.model.StoreCartVariation
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
import es.criosrango.shared.model.StoreProductExtensions as SharedStoreProductExtensions
import es.criosrango.shared.model.OutletOriginExtension as SharedOutletOriginExtension
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

    override suspend fun cart(): WooCart {
        Log.d("CriosRangoSharedCatalog", "CART source=shared operation=GET")
        return try { sharedClient.cart().toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun addCartItem(request: AddCartRequest): WooCart {
        Log.d("CriosRangoSharedCatalog", "CART source=shared operation=ADD")
        return try {
            sharedClient.addCartItem(request.toShared()).toAndroid()
        } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun updateCartItem(key: String, quantity: Int): WooCart {
        Log.d("CriosRangoSharedCatalog", "CART source=shared operation=UPDATE")
        return try { sharedClient.updateCartItem(key, quantity).toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun removeCartItem(key: String): WooCart {
        Log.d("CriosRangoSharedCatalog", "CART source=shared operation=REMOVE")
        return try { sharedClient.removeCartItem(key).toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

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

    override suspend fun checkout(): CheckoutResponse {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=GET")
        return try { sharedClient.checkout().toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun updateCustomer(request: UpdateCustomerRequest): WooCart {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=UPDATE_CUSTOMER")
        return try { sharedClient.updateCustomer(request.toShared()).toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun selectShippingRate(request: SelectShippingRateRequest): WooCart {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=SELECT_SHIPPING")
        return try { sharedClient.selectShippingRate(request.toShared()).toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
    }

    override suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=CREATE_ORDER")
        return try { sharedClient.createCheckout(request.toShared()).toAndroid() } catch (exception: Exception) { throw exception.toAndroidCatalogException() }
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
    extensions = extensions?.toAndroid(),
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

private fun SharedStoreProductExtensions.toAndroid(): StoreProductExtensions = StoreProductExtensions(
    criosrangoOutlet = criosrangoOutlet?.let { OutletOriginExtension(it.originalCategoryIds) }
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
    is StoreApiException -> CartException(message)
    is HttpRequestTimeoutException -> SocketTimeoutException(message).also { it.initCause(this) }
    is ResponseException -> {
        val code = response.status.value
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))
    }
    else -> this
}


private fun AddCartRequest.toShared(): StoreCartRequest = StoreCartRequest(
    id = id,
    quantity = quantity,
    variation = variation.map { StoreCartVariation(attribute = it.attribute, value = it.value) }
)

private fun StoreCart.toAndroid(): WooCart = WooCart(
    items = items.map { line ->
        CartLine(
            key = line.key,
            id = line.id,
            name = line.name,
            quantity = line.quantity,
            quantityLimits = line.quantityLimits?.let { QuantityLimits(it.minimum, it.maximum, it.multipleOf) },
            prices = line.prices.toAndroid(),
            totals = CartLineTotals(
                linePrice = line.totals.linePrice,
                linePriceTax = line.totals.linePriceTax,
                lineSubtotal = line.totals.lineSubtotal,
                lineSubtotalTax = line.totals.lineSubtotalTax,
                lineTotal = line.totals.lineTotal,
                lineTotalTax = line.totals.lineTotalTax,
                discount = line.totals.discount,
                discountTax = line.totals.discountTax
            ),
            images = line.images.map(SharedProductImage::toAndroid),
            variation = line.variation.map { CartVariation(it.attribute, it.value) }
        )
    },
    coupons = coupons.map { CartCoupon(it.code, it.label, CartCouponTotals(it.totals.totalDiscount, it.totals.totalDiscountTax)) },
    totals = CartTotals(
        totalItems = totals.totalItems,
        totalItemsTax = totals.totalItemsTax,
        totalFees = totals.totalFees,
        totalFeesTax = totals.totalFeesTax,
        totalDiscount = totals.totalDiscount,
        totalDiscountTax = totals.totalDiscountTax,
        totalShipping = totals.totalShipping,
        totalShippingTax = totals.totalShippingTax,
        totalPrice = totals.totalPrice,
        totalTax = totals.totalTax,
        currencySymbol = totals.currencySymbol,
        currencyMinorUnit = totals.currencyMinorUnit
    ),
    paymentMethods = paymentMethods,
    shippingRates = shippingRates.map { pkg ->
        ShippingRate(
            packageId = pkg.packageId,
            name = pkg.name,
            destination = pkg.destination?.let { ShippingDestination(it.address1, it.city, it.state, it.postcode, it.country) },
            rates = pkg.rates.map { rate ->
                ShippingOption(
                    rateId = rate.rateId,
                    name = rate.name,
                    methodId = rate.methodId,
                    price = rate.price,
                    taxes = rate.taxes,
                    currencySymbol = rate.currencySymbol,
                    currencyMinorUnit = rate.currencyMinorUnit,
                    selected = rate.selected
                )
            }
        )
    },
    shippingPackages = null,
    itemsCount = itemsCount,
    errors = errors.map { CartError(it.code, it.message) }
)

private fun CustomerAddress.toShared(): SharedCustomerAddress = SharedCustomerAddress(
    firstName = firstName, lastName = lastName, email = email, phone = phone,
    address1 = address1, postcode = postcode, city = city, state = state, country = country
)

private fun UpdateCustomerRequest.toShared(): SharedUpdateCustomerRequest =
    SharedUpdateCustomerRequest(billingAddress = billingAddress.toShared(), shippingAddress = shippingAddress.toShared())

private fun SelectShippingRateRequest.toShared(): SharedSelectShippingRateRequest =
    SharedSelectShippingRateRequest(packageId = packageId, rateId = rateId)

private fun CreateOrderRequest.toShared(): SharedCreateOrderRequest =
    SharedCreateOrderRequest(
        paymentMethod = paymentMethod,
        billingAddress = billing_address.toShared(),
        shippingAddress = shipping_address.toShared(),
        shippingRate = shippingRate,
        expectedTotal = expectedTotal,
        paymentData = paymentData,
        customerNote = customerNote
    )

private fun SharedCheckoutResponse.toAndroid(): CheckoutResponse = CheckoutResponse(
    orderId = orderId,
    orderKey = orderKey,
    orderNumber = orderNumber,
    status = status,
    paymentMethod = paymentMethod,
    paymentMethods = paymentMethods,
    paymentRequirements = paymentRequirements,
    redirectUrl = redirectUrl,
    paymentResult = paymentResult?.let { PaymentResult(
        paymentStatus = it.paymentStatus,
        redirectUrl = it.redirectUrl,
        paymentUrl = it.paymentUrl,
        paymentDetails = it.paymentDetails.map { detail -> PaymentDetail(detail.key, detail.value) }
    ) },
    experimentalCart = experimentalCart?.let { cart -> ExperimentalCart(
        paymentMethods = cart.paymentMethods,
        paymentRequirements = cart.paymentRequirements,
        totals = cart.totals.toAndroid()
    ) },
    totals = totals.toAndroid(),
    errors = errors.map { CartError(it.code, it.message) }
)

private fun es.criosrango.shared.model.StoreCartTotals.toAndroid(): CartTotals = CartTotals(
    totalItems = totalItems,
    totalItemsTax = totalItemsTax,
    totalFees = totalFees,
    totalFeesTax = totalFeesTax,
    totalDiscount = totalDiscount,
    totalDiscountTax = totalDiscountTax,
    totalShipping = totalShipping,
    totalShippingTax = totalShippingTax,
    totalPrice = totalPrice,
    totalTax = totalTax,
    currencySymbol = currencySymbol,
    currencyMinorUnit = currencyMinorUnit
)
