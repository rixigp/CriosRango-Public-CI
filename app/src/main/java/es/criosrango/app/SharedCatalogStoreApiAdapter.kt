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
import es.criosrango.shared.model.CustomerAddress as SharedCustomerAddress
import es.criosrango.shared.model.UpdateCustomerRequest as SharedUpdateCustomerRequest
import es.criosrango.shared.model.SelectShippingRateRequest as SharedSelectShippingRateRequest
import es.criosrango.shared.model.CreateOrderRequest as SharedCreateOrderRequest
import es.criosrango.shared.model.PaymentStatusResponse as SharedPaymentStatusResponse
import es.criosrango.shared.model.CheckoutResponse as SharedCheckoutResponse
import es.criosrango.shared.model.OutletOriginExtension as SharedOutletOriginExtension
import es.criosrango.shared.model.OutletAvailability as SharedOutletAvailability
import es.criosrango.shared.model.VariationAttribute as SharedVariationAttribute
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import java.net.SocketTimeoutException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient

private data class WooBrandImageDto(
    val src: String = "",
    val thumbnail: String = ""
)

private data class WooBrandDto(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val count: Int = 0,
    val image: WooBrandImageDto? = null
)

private interface WooBrandStoreApi {
    @GET("products/brands")
    suspend fun brands(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("hide_empty") hideEmpty: Boolean = true
    ): List<WooBrandDto>

    @GET("products")
    suspend fun productsByBrand(
        @Query("per_page") perPage: Int,
        @Query("page") page: Int,
        @Query("brand") brand: String
    ): List<StoreProduct>
}

private fun createWooBrandStoreApi(session: StoreSession): WooBrandStoreApi {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            session.cartToken?.takeIf { it.isNotBlank() }?.let { builder.header("Cart-Token", it) }
            session.nonce?.takeIf { it.isNotBlank() }?.let { builder.header("Nonce", it) }
            session.cookieHeader?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            val response = chain.proceed(builder.build())
            session.update(response.headers)
            response
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(STORE_API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WooBrandStoreApi::class.java)
}

/**
 * Temporary Phase C bridge between the stable Android StoreApi contract and
 * the validated shared KMP catalog client.
 *
 * Android catalog/cart/checkout/payment-status operations are routed through the shared KMP StoreApiClient.
 */
class SharedCatalogStoreApiAdapter(
    private val sharedClient: StoreApiClient,
    session: StoreSession
) : StoreApi {
    private val brandApi: WooBrandStoreApi = createWooBrandStoreApi(session)

    override suspend fun cart(): WooCart {
        Log.d("CriosRangoSharedCart", "CART source=shared operation=GET")
        return try {
            sharedClient.cart().toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun addCartItem(request: AddCartRequest): WooCart {
        Log.d("CriosRangoSharedCart", "CART source=shared operation=ADD")
        return try {
            sharedClient.addCartItem(request.toShared()).toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun updateCartItem(key: String, quantity: Int): WooCart {
        Log.d("CriosRangoSharedCart", "CART source=shared operation=UPDATE key=$key quantity=$quantity")
        return try {
            sharedClient.updateCartItem(key, quantity).toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun removeCartItem(key: String): WooCart {
        Log.d("CriosRangoSharedCart", "CART source=shared operation=REMOVE key=$key")
        return try {
            sharedClient.removeCartItem(key).toAndroid()
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }
    override suspend fun checkout(): CheckoutResponse = try {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=GET")
        sharedClient.checkout().toAndroid()
    } catch (exception: Exception) { throw exception.toAndroidCatalogException() }

    override suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse = try {
        Log.d("CriosRangoSharedCheckout", "CHECKOUT source=shared operation=POST")
        sharedClient.createCheckout(request.toShared()).toAndroid()
    } catch (exception: Exception) { throw exception.toAndroidCatalogException() }

    override suspend fun getOrderStatus(orderId: Int, orderKey: String): OrderStatusResponse {
        Log.d("CriosRangoSharedPayment", "PAYMENT_STATUS source=shared operation=GET orderId=$orderId")
        return sharedClient.paymentStatus(orderId, orderKey).toAndroid()
    }

    override suspend fun selectShippingRate(request: SelectShippingRateRequest): WooCart = try {
        Log.d("CriosRangoSharedShipping", "SHIPPING source=shared operation=POST")
        sharedClient.selectShippingRate(request.toShared()).toAndroid()
    } catch (exception: Exception) { throw exception.toAndroidCatalogException() }

    override suspend fun updateCustomer(request: UpdateCustomerRequest): WooCart = try {
        Log.d("CriosRangoSharedCheckout", "CUSTOMER source=shared operation=POST")
        sharedClient.updateCustomer(request.toShared()).toAndroid()
    } catch (exception: Exception) { throw exception.toAndroidCatalogException() }

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

    override suspend fun outletAvailability(): SharedOutletAvailability {
        Log.d("CriosRangoSharedCatalog", "OUTLET_AVAILABILITY source=shared operation=GET")
        return try {
            sharedClient.outletAvailability()
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
        Log.d(
            "CriosRangoSharedCatalog",
            "PRODUCTS source=shared StoreApiClient perPage=$perPage page=$page search=${search != null} category=$category orderby=$orderBy order=$order after=${after != null} featured=$featured"
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

    override suspend fun brands(perPage: Int, page: Int): List<BrandTerm> {
        Log.d("CriosRangoBrands", "BRANDS source=product_brand perPage=$perPage page=$page")
        return try {
            brandApi.brands(perPage = perPage, page = page)
                .filter { it.name.isNotBlank() && it.slug.isNotBlank() }
                .map { brand ->
                    BrandTerm(
                        id = brand.id,
                        name = brand.name,
                        slug = brand.slug,
                        count = brand.count,
                        imageUrl = brand.image?.src?.takeIf { it.isNotBlank() }
                            ?: brand.image?.thumbnail?.takeIf { it.isNotBlank() }
                    ).withPackagedLogoFallback()
                }
        } catch (exception: Exception) {
            throw exception.toAndroidCatalogException()
        }
    }

    override suspend fun productsByBrand(
        perPage: Int,
        page: Int,
        brand: String
    ): List<StoreProduct> {
        Log.d("CriosRangoBrands", "PRODUCTS source=product_brand brand=$brand perPage=$perPage page=$page")
        return try {
            brandApi.productsByBrand(
                perPage = perPage,
                page = page,
                brand = brand
            )
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

internal fun SharedProductImage.toAndroid(): ProductImage = ProductImage(
    src = src,
    thumbnail = thumbnail,
    srcSet = srcSet,
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
    is retrofit2.HttpException -> StoreApiException(
        statusCode = code(),
        apiCode = null,
        message = message()
    )
    is ResponseException -> StoreApiException(
        statusCode = response.status.value,
        apiCode = null,
        message = response.status.description
    )
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

private fun SharedPaymentStatusResponse.toAndroid(): OrderStatusResponse = OrderStatusResponse(
    id = id,
    status = status,
    paid = paid,
    needsPayment = needsPayment,
    terminal = terminal
)
