package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.model.CustomerAddress
import es.criosrango.shared.model.supportedPaymentOptions

@Composable
fun IosCheckoutScreen(
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    accountRepository: AccountRepository,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit
) {
    val checkout by checkoutStore.checkout.collectAsState()
    val cart by checkoutStore.cart.collectAsState()
    val phase by checkoutStore.phase.collectAsState()
    val error by checkoutStore.error.collectAsState()
    val accountAddress by checkoutStore.accountAddress.collectAsState()
    val createdOrder by checkoutStore.createdOrder.collectAsState()
    val paymentState by paymentStore.state.collectAsState()
    val paymentError by paymentStore.error.collectAsState()
    val paymentOrderId by paymentStore.orderId.collectAsState()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var address2 by remember { mutableStateOf("") }
    var postcode by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("ES") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var selectedPayment by remember { mutableStateOf("") }
    var selectedShipping by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(Unit) { checkoutStore.load() }
    LaunchedEffect(accountAddress) {
        accountAddress?.let {
            firstName = it.firstName
            lastName = it.lastName
            email = it.email
            phone = it.phone
            address = it.address1
            address2 = it.address2
            postcode = it.postcode
            city = it.city
            state = it.state
            country = it.country.ifBlank { "ES" }
        }
    }

    val paymentOptions = checkout?.supportedPaymentOptions().orEmpty()
    LaunchedEffect(paymentOptions) {
        if (selectedPayment !in paymentOptions.map { it.gatewayId }) {
            selectedPayment = paymentOptions.firstOrNull()?.gatewayId.orEmpty()
        }
    }
    LaunchedEffect(cart.shippingRates) {
        val selected = cart.shippingRates.flatMap { pack ->
            pack.rates.filter { it.selected }.map { pack.packageId to it.rateId }
        }.firstOrNull()
        if (selected != null) selectedShipping = selected
    }

    LaunchedEffect(createdOrder?.orderId, createdOrder?.paymentMethod) {
        val order = createdOrder ?: return@LaunchedEffect
        if (order.paymentMethod.equals("cecabank_gateway", ignoreCase = true)) {
            paymentStore.startCardPayment(order)?.let { onOpenPayment(it) }
        }
    }

    val addressValue = CustomerAddress(
        firstName.trim(), lastName.trim(), email.trim(), phone.trim(),
        address.trim(), address2.trim(), postcode.trim(), city.trim(), state.trim(), country.trim()
    )
    val shippingOptions = cart.shippingRates.flatMap { pack -> pack.rates.map { pack to it } }
    val canSubmit = phase == StoreCheckoutPhase.READY &&
        selectedShipping != null &&
        selectedPayment.isNotBlank() &&
        addressError == null &&
        createdOrder == null &&
        paymentState !in setOf(StoreCardPaymentState.OPENING, StoreCardPaymentState.WAITING_RETURN, StoreCardPaymentState.RECONCILING)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Carrito") }
                Text("Finalizar compra", style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (phase == StoreCheckoutPhase.LOADING && checkout == null) item { CircularProgressIndicator() }
        item { Text("Dirección", style = MaterialTheme.typography.titleLarge) }
        item { CheckoutField("Nombre", firstName) { firstName = it } }
        item { CheckoutField("Apellidos", lastName) { lastName = it } }
        item { CheckoutField("Email", email, KeyboardType.Email) { email = it } }
        item { CheckoutField("Teléfono", phone, KeyboardType.Phone) { phone = it } }
        item { CheckoutField("Dirección", address) { address = it } }
        item { CheckoutField("Piso / puerta (opcional)", address2) { address2 = it } }
        item { CheckoutField("Código postal", postcode, KeyboardType.Number) { postcode = it.filter(Char::isDigit).take(5) } }
        item { CheckoutField("Localidad", city) { city = it } }
        item { CheckoutField("Provincia", state) { state = it } }
        item { CheckoutField("País", country) { country = it.uppercase().take(2) } }
        addressError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = {
                    addressError = validateIosCheckoutAddress(addressValue)
                    if (addressError == null) checkoutStore.updateCustomer(addressValue)
                },
                enabled = phase != StoreCheckoutPhase.LOADING && phase != StoreCheckoutPhase.CREATING_ORDER,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Consultar entrega") }
        }
        item { Text("Envío", style = MaterialTheme.typography.titleLarge) }
        if (shippingOptions.isEmpty() && checkout != null) item { Text("No hay tarifas disponibles para esta dirección.") }
        items(shippingOptions) { pair ->
            val pack = pair.first
            val rate = pair.second
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedShipping = pack.packageId to rate.rateId
                    checkoutStore.selectShipping(pack.packageId, rate.rateId)
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedShipping == (pack.packageId to rate.rateId),
                    onClick = {
                        selectedShipping = pack.packageId to rate.rateId
                        checkoutStore.selectShipping(pack.packageId, rate.rateId)
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text(rate.name)
                    Text(rate.price + " " + rate.currencySymbol, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { HorizontalDivider(); Text("Método de pago", style = MaterialTheme.typography.titleLarge) }
        if (paymentOptions.isEmpty() && checkout != null) item { Text("No hay métodos de pago disponibles.") }
        items(paymentOptions) { option ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { selectedPayment = option.gatewayId }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedPayment == option.gatewayId, onClick = { selectedPayment = option.gatewayId })
                Text(
                    when (option.kind) {
                        es.criosrango.shared.model.CheckoutPaymentKind.CARD -> "Pago con tarjeta"
                        es.criosrango.shared.model.CheckoutPaymentKind.BIZUM -> "Bizum"
                    }
                )
            }
        }
        item { Text("Total: " + cart.totals.totalPrice + " " + cart.totals.currencySymbol, style = MaterialTheme.typography.titleLarge) }

        if (paymentState == StoreCardPaymentState.PAID) item {
            Text("Pago confirmado para el pedido #" + (paymentOrderId ?: ""), color = MaterialTheme.colorScheme.primary)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Seguir comprando") }
        }
        if (paymentState == StoreCardPaymentState.NOT_PAID) item {
            Text("El pago no se ha completado.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { paymentStore.clearForNewProcess() }, modifier = Modifier.fillMaxWidth()) { Text("Volver a intentarlo") }
        }
        if (!error.isNullOrBlank()) item {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { checkoutStore.load() }) { Text("Reintentar checkout") }
        }
        if (!paymentError.isNullOrBlank()) item {
            Text(paymentError!!, color = MaterialTheme.colorScheme.error)
            if (paymentState == StoreCardPaymentState.ERROR) {
                TextButton(onClick = paymentStore::retryReconciliation) { Text("Reintentar comprobación") }
            }
        }

        createdOrder?.let { order ->
            val isBizum = order.paymentMethod.equals("bizum", ignoreCase = true) || order.paymentMethod.equals("cheque", ignoreCase = true)
            if (isBizum) item {
                Text("Pedido recibido", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("Pedido #" + (order.orderNumber ?: order.orderId) + " creado correctamente.")
                Text("Realiza el pago por Bizum al 679 97 28 88 y utiliza el número de pedido como referencia de pago.")
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Seguir comprando") }
            } else if (paymentState == StoreCardPaymentState.OPENING || paymentState == StoreCardPaymentState.WAITING_RETURN || paymentState == StoreCardPaymentState.RECONCILING) {
                Text("Pasarela de pago", style = MaterialTheme.typography.titleLarge)
                Text(if (paymentState == StoreCardPaymentState.RECONCILING) "Comprobando el pago…" else "Abriendo Cecabank…")
            }
        }

        item {
            Button(
                onClick = { checkoutStore.createOrder(addressValue, selectedPayment, selectedShipping!!.second) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (phase == StoreCheckoutPhase.CREATING_ORDER) "Creando pedido…" else "Pagar")
            }
        }
        if (phase == StoreCheckoutPhase.CREATING_ORDER) item { CircularProgressIndicator() }
    }
}

@Composable
private fun CheckoutField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
}

private fun validateIosCheckoutAddress(address: CustomerAddress): String? = when {
    address.firstName.isBlank() -> "Indica tu nombre."
    address.lastName.isBlank() -> "Indica tus apellidos."
    !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(address.email) -> "Introduce un email válido."
    address.phone.isBlank() -> "Indica un teléfono."
    address.address1.isBlank() -> "Indica una dirección."
    address.postcode.length != 5 || address.postcode.any { !it.isDigit() } -> "Introduce un código postal de 5 cifras."
    address.city.isBlank() -> "Indica una localidad."
    address.state.isBlank() -> "Indica una provincia."
    address.country != "ES" -> "Selecciona España."
    else -> null
}
