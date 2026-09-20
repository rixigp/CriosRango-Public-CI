package es.criosrango.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class AccountLoginRequest(
    val login: String,
    val password: String
)

data class AccountUser(
    val id: Int,
    val email: String,
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = ""
)

data class AccountRegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val phone: String = ""
)

data class AccountForgotPasswordRequest(
    val login: String
)

data class AccountMessageResponse(
    val success: Boolean = false,
    val message: String = ""
)

data class AccountLoginResponse(
    val token: String,
    val user: AccountUser
)

data class AccountMeResponse(
    val user: AccountUser
)

data class AccountCustomerAddress(
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("last_name") val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerializedName("address_1") val address1: String = "",
    @SerializedName("address_2") val address2: String = "",
    val postcode: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "ES"
)

data class AccountClaimOrderRequest(
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("order_key") val orderKey: String
)

data class AccountClaimOrderResponse(
    val success: Boolean,
    @SerializedName("order_id") val orderId: Int,
    @SerializedName("customer_id") val customerId: Int
)

data class AccountOrderVariation(
    val name: String = "",
    val value: String = ""
)

data class AccountOrderItem(
    val name: String = "",
    val quantity: Int = 0,
    val variations: List<AccountOrderVariation> = emptyList()
)

data class AccountOrderShippingAddress(
    @SerializedName("address_1") val address1: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = ""
)

data class AccountOrderSummary(
    val id: Int,
    val number: String,
    val status: String,
    @SerializedName("status_label") val statusLabel: String = "",
    @SerializedName("date_created") val dateCreated: String? = null,
    val total: String = "",
    val currency: String = "EUR",
    @SerializedName("payment_method_title") val paymentMethodTitle: String = "",
    val items: List<AccountOrderItem> = emptyList(),
    val subtotal: String = "",
    @SerializedName("shipping_total") val shippingTotal: String = "",
    @SerializedName("shipping_method") val shippingMethod: String = "",
    @SerializedName("shipping_address") val shippingAddress: AccountOrderShippingAddress = AccountOrderShippingAddress()
)

data class AccountOrdersResponse(
    val orders: List<AccountOrderSummary> = emptyList(),
    val total: Int = 0
)

interface AccountApi {
    @POST("wp-json/criosrango/v1/register")
    suspend fun register(@Body request: AccountRegisterRequest): AccountLoginResponse

    @POST("wp-json/criosrango/v1/forgot-password")
    suspend fun forgotPassword(@Body request: AccountForgotPasswordRequest): AccountMessageResponse

    @POST("wp-json/criosrango/v1/login")
    suspend fun login(@Body body: AccountLoginRequest): AccountLoginResponse

    @GET("wp-json/criosrango/v1/me")
    suspend fun me(@Header("Authorization") authorization: String): AccountMeResponse

    @GET("wp-json/criosrango/v1/customer-address")
    suspend fun customerAddress(@Header("Authorization") authorization: String): AccountCustomerAddress

    @POST("wp-json/criosrango/v1/customer-address-save")
    suspend fun saveCustomerAddress(
        @Header("Authorization") authorization: String,
        @Body address: AccountCustomerAddress
    )

    @GET("wp-json/criosrango/v1/orders-detailed")
    suspend fun orders(
        @Header("Authorization") authorization: String,
        @Query("per_page") perPage: Int = 20
    ): AccountOrdersResponse

    @POST("wp-json/criosrango/v1/claim-order")
    suspend fun claimOrder(
        @Header("Authorization") authorization: String,
        @Body request: AccountClaimOrderRequest
    ): AccountClaimOrderResponse

    @POST("wp-json/criosrango/v1/logout")
    suspend fun logout(@Header("Authorization") authorization: String)
}

class AccountSessionStore(context: Context) {
    private companion object {
        const val TOKEN = "account_token"
        const val PREFS = "criosrango_account_session_v2"
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        migrateLegacyToken(context)
    }

    @Suppress("DEPRECATION")
    private fun migrateLegacyToken(context: Context) {
        if (preferences.contains(TOKEN)) return

        val legacyToken = runCatching {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            val legacyPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "criosrango_account_session",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            legacyPreferences.getString(TOKEN, null)
        }.getOrNull()

        if (!legacyToken.isNullOrBlank()) {
            preferences.edit().putString(TOKEN, legacyToken).apply()
        }
    }

    var token: String?
        get() = preferences.getString(TOKEN, null)
        private set(value) {
            preferences.edit().apply {
                if (value.isNullOrBlank()) remove(TOKEN) else putString(TOKEN, value)
            }.apply()
        }

    fun save(token: String) {
        this.token = token
    }

    fun clear() {
        token = null
    }
}

class AccountRepository(context: Context) {
    private val session = AccountSessionStore(context.applicationContext)

    private val api: AccountApi = Retrofit.Builder()
        .baseUrl("https://criosrango.es/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AccountApi::class.java)

    val hasSession: Boolean
        get() = !session.token.isNullOrBlank()

    private fun authorization(): String {
        val token = session.token ?: throw IllegalStateException("No hay ninguna sesión iniciada.")
        return "Bearer $token"
    }

    suspend fun login(login: String, password: String): AccountUser {
        val response = api.login(AccountLoginRequest(login.trim(), password))
        session.save(response.token)
        return response.user
    }

    suspend fun registerAccount(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ): AccountUser {
        val response = api.register(
            AccountRegisterRequest(
                email = email.trim(),
                password = password,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = phone.trim()
            )
        )
        session.save(response.token)
        return response.user
    }

    suspend fun forgotPassword(login: String): String =
        api.forgotPassword(AccountForgotPasswordRequest(login.trim())).message

    suspend fun me(): AccountUser = api.me(authorization()).user

    suspend fun saveCustomerAddress(address: AccountCustomerAddress) {
        api.saveCustomerAddress(authorization(), address)
    }

    suspend fun customerAddress(): AccountCustomerAddress = api.customerAddress(authorization())

    suspend fun updateAccountDetails(firstName: String, lastName: String) {
        val currentAddress = runCatching { customerAddress() }.getOrNull()
            ?: AccountCustomerAddress()

        api.saveCustomerAddress(
            authorization(),
            currentAddress.copy(
                firstName = firstName,
                lastName = lastName
            )
        )
    }

    suspend fun orders(): List<AccountOrderSummary> = api.orders(authorization(), 20).orders

    suspend fun claimOrder(orderId: Int, orderKey: String) {
        api.claimOrder(
            authorization(),
            AccountClaimOrderRequest(orderId, orderKey)
        )
    }

    suspend fun logout() {
        try {
            if (hasSession) api.logout(authorization())
        } finally {
            session.clear()
        }
    }

    fun clearLocalSession() {
        session.clear()
    }
}

enum class AccountAuthState { CHECKING, AUTHENTICATED, UNAUTHENTICATED }

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AccountRepository(application)

    private val _authState = MutableStateFlow(AccountAuthState.CHECKING)
    val authState = _authState.asStateFlow()

    private val _user = MutableStateFlow<AccountUser?>(null)
    val user = _user.asStateFlow()

    private val _orders = MutableStateFlow<List<AccountOrderSummary>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _address = MutableStateFlow<AccountCustomerAddress?>(null)
    val address = _address.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _accountError = MutableStateFlow<StoreUiError?>(null)
    val accountError = _accountError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private val _savingAccountDetails = MutableStateFlow(false)
    val savingAccountDetails = _savingAccountDetails.asStateFlow()

    private val _accountDataError = MutableStateFlow<String?>(null)
    val accountDataError = _accountDataError.asStateFlow()

    private val _accountDataNotice = MutableStateFlow<String?>(null)
    val accountDataNotice = _accountDataNotice.asStateFlow()

    private val _savingAddress = MutableStateFlow(false)
    val savingAddress = _savingAddress.asStateFlow()

    private val _addressSaveError = MutableStateFlow<String?>(null)
    val addressSaveError = _addressSaveError.asStateFlow()

    private val _addressSaveNotice = MutableStateFlow<String?>(null)
    val addressSaveNotice = _addressSaveNotice.asStateFlow()

    private val deliveryAddressStore =
        DeliveryAddressStore(application.getSharedPreferences("criosrango", Context.MODE_PRIVATE))

    private var restoreJob: kotlinx.coroutines.Job? = null
    private var accountGeneration = 0L

    init {
        restoreSession()
    }

    private fun syncCheckoutAddress(address: AccountCustomerAddress) {
        deliveryAddressStore.save(
            CustomerAddress(
                firstName = address.firstName,
                lastName = address.lastName,
                email = address.email,
                phone = address.phone,
                address1 = address.address1,
                postcode = address.postcode,
                city = address.city,
                state = address.state,
                country = address.country
            )
        )
    }

    private fun invalidateSession() {
        accountGeneration++
        restoreJob?.cancel()
        repository.clearLocalSession()
        _user.value = null
        _address.value = null
        _orders.value = emptyList()
        _authState.value = AccountAuthState.UNAUTHENTICATED
        _error.value = null
        _accountError.value = StoreUiError(StoreErrorType.SESSION_EXPIRED)
        _notice.value = null
        _loading.value = false
        _savingAccountDetails.value = false
        _savingAddress.value = false
    }

    private fun handleAuthenticatedHttpError(exception: Exception, fallback: String): Boolean {
        if (exception is HttpException && exception.code() == 401) {
            invalidateSession()
            return true
        }
        _accountError.value = exception.toStoreUiError(authenticated = true)
        _error.value = fallback
        return false
    }

    private fun restoreSession() {
        restoreJob?.cancel()
        if (!repository.hasSession) {
            _authState.value = AccountAuthState.UNAUTHENTICATED
            _loading.value = false
            return
        }

        val generation = ++accountGeneration
        _authState.value = AccountAuthState.CHECKING
        _loading.value = true
        _error.value = null
        _accountError.value = null

        restoreJob = viewModelScope.launch {
            try {
                val restoredUser = repository.me()
                if (generation != accountGeneration) return@launch
                _user.value = restoredUser
                _authState.value = AccountAuthState.AUTHENTICATED

                try {
                    val loadedAddress = repository.customerAddress()
                    if (generation == accountGeneration) {
                        _address.value = loadedAddress
                        syncCheckoutAddress(loadedAddress)
                    }
                } catch (exception: Exception) {
                    if (generation != accountGeneration) return@launch
                    if (handleAuthenticatedHttpError(exception, "No hemos podido cargar tus datos de cuenta.")) return@launch
                }

                try {
                    val loadedOrders = repository.orders()
                    if (generation == accountGeneration) _orders.value = loadedOrders
                } catch (exception: Exception) {
                    if (generation != accountGeneration) return@launch
                    if (handleAuthenticatedHttpError(exception, "No hemos podido cargar tus pedidos.")) return@launch
                }
            } catch (exception: Exception) {
                if (generation != accountGeneration) return@launch
                if ((exception is HttpException && exception.code() == 401) || !repository.hasSession) {
                    invalidateSession()
                } else {
                    // Keep the stored token and remain in CHECKING: the UI offers a retry
                    // instead of asking for credentials that may still be valid.
                    _accountError.value = exception.toStoreUiError()
                    _error.value = null
                    _loading.value = false
                }
            } finally {
                if (generation == accountGeneration && _authState.value == AccountAuthState.CHECKING) {
                    _loading.value = false
                }
            }
        }
    }

    fun retrySession() {
        if (_authState.value == AccountAuthState.CHECKING && !loading.value) restoreSession()
    }

    fun clearAccountMessages() {
        _error.value = null
        _accountError.value = null
        _notice.value = null
    }

    fun clearAccountDataMessages() {
        _accountDataError.value = null
        _accountDataNotice.value = null
    }

    fun clearAddressSaveMessages() {
        _addressSaveError.value = null
        _addressSaveNotice.value = null
    }

    fun updateAccountDetails(firstName: String, lastName: String) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()

        if (cleanFirstName.isBlank() || cleanLastName.isBlank()) {
            _accountDataError.value = "Introduce tu nombre y apellidos."
            _accountDataNotice.value = null
            return
        }

        val current = _user.value ?: return
        val generation = accountGeneration

        viewModelScope.launch {
            _savingAccountDetails.value = true
            _accountDataError.value = null
            _accountDataNotice.value = null

            try {
                repository.updateAccountDetails(cleanFirstName, cleanLastName)
                if (generation != accountGeneration || _authState.value != AccountAuthState.AUTHENTICATED) return@launch
                _user.value = current.copy(
                    firstName = cleanFirstName,
                    lastName = cleanLastName,
                    displayName = listOf(cleanFirstName, cleanLastName).joinToString(" ")
                )
                _address.value = _address.value?.copy(
                    firstName = cleanFirstName,
                    lastName = cleanLastName
                )
                _accountDataNotice.value = "Datos actualizados"
            } catch (exception: Exception) {
                if (!handleAuthenticatedHttpError(exception, "No se han podido guardar los cambios")) {
                    _accountDataError.value = "No se han podido guardar los cambios"
                }
            } finally {
                if (generation == accountGeneration) _savingAccountDetails.value = false
            }
        }
    }

    fun createAccount(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String
    ) {
        if (firstName.isBlank() || lastName.isBlank()) {
            _error.value = "Introduce tu nombre y apellidos."
            return
        }
        if (email.isBlank()) {
            _error.value = "Introduce tu correo electrónico."
            return
        }
        if (password.length < 8) {
            _error.value = "La contraseña debe tener al menos 8 caracteres."
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _notice.value = null
            try {
                val created = repository.registerAccount(email, password, firstName, lastName, phone)
                _user.value = created
                _authState.value = AccountAuthState.AUTHENTICATED
                val loadedAddress = try { repository.customerAddress() } catch (exception: Exception) {
                    if (exception is HttpException && exception.code() == 401) {
                        invalidateSession()
                        return@launch
                    }
                    null
                }
                _address.value = loadedAddress
                loadedAddress?.let(::syncCheckoutAddress)
                _orders.value = try { repository.orders() } catch (exception: Exception) {
                    if (exception is HttpException && exception.code() == 401) {
                        invalidateSession()
                        return@launch
                    }
                    emptyList()
                }
            } catch (e: HttpException) {
                _error.value = when (e.code()) {
                    400 -> "Revisa los datos introducidos."
                    409 -> "Ya existe una cuenta con ese correo electrónico."
                    429 -> "Demasiados intentos. Espera unos minutos."
                    else -> "No hemos podido crear la cuenta."
                }
            } catch (_: Exception) {
                _error.value = "No hemos podido crear la cuenta."
            } finally {
                _loading.value = false
            }
        }
    }

    fun forgotPassword(login: String) {
        if (login.isBlank()) {
            _error.value = "Introduce tu correo o usuario."
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _notice.value = null
            try {
                _notice.value = repository.forgotPassword(login)
            } catch (e: HttpException) {
                _error.value = when (e.code()) {
                    429 -> "Demasiadas solicitudes. Espera unos minutos."
                    else -> "No hemos podido solicitar el cambio de contraseña."
                }
            } catch (_: Exception) {
                _error.value = "No hemos podido solicitar el cambio de contraseña."
            } finally {
                _loading.value = false
            }
        }
    }

    fun login(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) {
            _error.value = "Introduce tu correo y contraseña."
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _notice.value = null
            try {
                val loggedUser = repository.login(login, password)
                _user.value = loggedUser
                _authState.value = AccountAuthState.AUTHENTICATED
                val loadedAddress = repository.customerAddress()
                _address.value = loadedAddress
                syncCheckoutAddress(loadedAddress)
                _orders.value = repository.orders()
            } catch (e: HttpException) {
                _authState.value = if (repository.hasSession) AccountAuthState.CHECKING else AccountAuthState.UNAUTHENTICATED
                _error.value = when (e.code()) {
                    401 -> "El correo o la contraseña no son correctos."
                    429 -> "Demasiados intentos. Espera unos minutos."
                    else -> "No hemos podido iniciar sesión."
                }
            } catch (_: Exception) {
                _error.value = "No hemos podido conectar con la tienda."
            } finally {
                _loading.value = false
            }
        }
    }

    fun claimOrder(orderId: Int, orderKey: String) {
        if (_user.value == null || orderKey.isBlank()) return
        val generation = accountGeneration
        viewModelScope.launch {
            try {
                repository.claimOrder(orderId, orderKey)
                if (generation == accountGeneration) _orders.value = repository.orders()
            } catch (exception: Exception) {
                if (generation != accountGeneration) return@launch
                if (!handleAuthenticatedHttpError(exception, "No se ha podido actualizar el pedido.")) {
                    android.util.Log.e("CriosRangoAccount", "Order link failed", exception)
                }
            }
        }
    }

    fun saveAddress(address: AccountCustomerAddress) {
        if (_user.value == null) return

        val clean = address.copy(
            firstName = address.firstName.trim(),
            lastName = address.lastName.trim(),
            address1 = address.address1.trim(),
            address2 = address.address2.trim(),
            postcode = address.postcode.trim(),
            city = address.city.trim(),
            state = address.state.trim(),
            country = "ES"
        )

        if (
            clean.firstName.isBlank() ||
            clean.lastName.isBlank() ||
            clean.address1.isBlank() ||
            clean.postcode.isBlank() ||
            clean.city.isBlank() ||
            clean.state.isBlank()
        ) {
            _addressSaveError.value = "Completa todos los campos obligatorios."
            _addressSaveNotice.value = null
            return
        }

        if (!clean.postcode.matches(Regex("\\d{5}"))) {
            _addressSaveError.value = "Introduce un código postal válido."
            _addressSaveNotice.value = null
            return
        }

        if (SPANISH_PROVINCES.none { it.code.equals(clean.state, ignoreCase = true) }) {
            _addressSaveError.value = "Selecciona una provincia."
            _addressSaveNotice.value = null
            return
        }

        val generation = accountGeneration
        viewModelScope.launch {
            _savingAddress.value = true
            _addressSaveError.value = null
            _addressSaveNotice.value = null
            try {
                repository.saveCustomerAddress(clean)
                if (generation != accountGeneration || _authState.value != AccountAuthState.AUTHENTICATED) return@launch
                _address.value = clean
                syncCheckoutAddress(clean)
                _addressSaveNotice.value = "Dirección actualizada"
            } catch (exception: Exception) {
                if (handleAuthenticatedHttpError(exception, "No se ha podido guardar la dirección")) return@launch
                _addressSaveError.value = "No se ha podido guardar la dirección"
            } finally {
                if (generation == accountGeneration) _savingAddress.value = false
            }
        }
    }

    fun refreshOrders() {
        if (_user.value == null || _authState.value != AccountAuthState.AUTHENTICATED) return

        val generation = accountGeneration
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _accountError.value = null
            try {
                val loaded = repository.orders()
                if (generation == accountGeneration) _orders.value = loaded
            } catch (exception: Exception) {
                if (generation == accountGeneration) {
                    handleAuthenticatedHttpError(exception, "No hemos podido actualizar tus pedidos.")
                }
            } finally {
                if (generation == accountGeneration) _loading.value = false
            }
        }
    }

    fun logout() {
        val generation = ++accountGeneration
        restoreJob?.cancel()
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.logout()
            } finally {
                if (generation == accountGeneration) {
                    _user.value = null
                    _address.value = null
                    _orders.value = emptyList()
                    _authState.value = AccountAuthState.UNAUTHENTICATED
                    _error.value = null
                    _accountError.value = null
                    _notice.value = null
                    _loading.value = false
                    _savingAccountDetails.value = false
                    _savingAddress.value = false
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
