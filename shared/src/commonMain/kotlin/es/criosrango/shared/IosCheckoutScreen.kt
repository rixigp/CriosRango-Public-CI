package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.model.CustomerAddress
import es.criosrango.shared.model.supportedPaymentOptions
import es.criosrango.shared.model.consumerDiscount
import es.criosrango.shared.validateCheckoutAddress
import es.criosrango.shared.CHECKOUT_SPANISH_PROVINCES
import es.criosrango.shared.model.normalizePaymentGatewayIds
import es.criosrango.shared.model.rawPaymentGatewayIds
import es.criosrango.shared.model.consumerShipping
import es.criosrango.shared.model.consumerSubtotal
import es.criosrango.shared.checkoutFreeShippingProgress
import es.criosrango.shared.loyalty.LoyaltyRepository
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.resolveCouponPresentation

@Composable
fun StoreCheckoutScreen(
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    accountRepository: AccountRepository,
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    loyaltyRepository: LoyaltyRepository,
    walletEnabled: Boolean,
    cartQuantity: Int = 0,
    onSearch: () -> Unit = {},
    onCart: () -> Unit = {}
) {
    val checkout by checkoutStore.checkout.collectAsState()
    val cart by checkoutStore.cart.collectAsState()
    val phase by checkoutStore.phase.collectAsState()
    val error by checkoutStore.error.collectAsState()
    val accountAddress by checkoutStore.accountAddress.collectAsState()
    val createdOrder by checkoutStore.createdOrder.collectAsState()
    val paymentState by paymentStore.state.collectAsState()
    val paymentError by paymentStore.error.collectAsState()
    var pendingRewards by remember { mutableStateOf<List<LoyaltyReward>>(emptyList()) }
    LaunchedEffect(walletEnabled) {
        pendingRewards = if (walletEnabled) runCatching { loyaltyRepository.getWallet().pendingRewards }.getOrDefault(emptyList()) else emptyList()
    }

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

    val paymentOptions = normalizePaymentGatewayIds(checkout?.rawPaymentGatewayIds().orEmpty() + cart.paymentMethods)
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
        address.trim(), address2.trim(), postcode.trim(), city.trim(), state.trim().uppercase(), country.trim().uppercase()
    )
    val shippingOptions = cart.shippingRates.flatMap { pack -> pack.rates.map { pack to it } }
    val paymentInProgress = paymentState in setOf(
        StoreCardPaymentState.OPENING,
        StoreCardPaymentState.WAITING_RETURN,
        StoreCardPaymentState.RECONCILING
    )
    val checkoutBusy = phase == StoreCheckoutPhase.LOADING || phase == StoreCheckoutPhase.CREATING_ORDER
    val freeShippingProgress = checkoutFreeShippingProgress(
        cart.totals.consumerSubtotal(),
        cart.totals.currencyMinorUnit
    )
    val freeShippingMessage = if (freeShippingProgress.reached) {
        "¡Ya tienes envío gratis!"
    } else {
        "Te faltan ${formatStorePrice(freeShippingProgress.remainingMinorUnits.toString(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol)} para conseguir envío gratis"
    }

    val canSubmit = cart.items.isNotEmpty() &&
        phase == StoreCheckoutPhase.READY &&
        selectedShipping != null &&
        selectedPayment.isNotBlank() &&
        addressError == null &&
        createdOrder == null &&
        !paymentInProgress

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { StoreGlobalTopBar(cartQuantity = cartQuantity, onSearch = onSearch, onCart = onCart) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, enabled = !checkoutBusy && !paymentInProgress) { Text("←") }
                Text("Finalizar compra", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text("🚚  " + freeShippingMessage, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        if (phase == StoreCheckoutPhase.LOADING && checkout == null) item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Cargando checkout…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (cart.items.isEmpty() && phase != StoreCheckoutPhase.LOADING) item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("El carrito está vacío", style = MaterialTheme.typography.titleMedium)
                    Text("Añade productos al carrito antes de continuar con la compra.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onBack) { Text("Volver al carrito") }
                }
            }
        }
        item { CheckoutSection(1, "Entrega") }
        item { CheckoutField("Nombre", firstName) { firstName = it } }
        item { CheckoutField("Apellidos", lastName) { lastName = it } }
        item { CheckoutField("Email", email, KeyboardType.Email) { email = it } }
        item { CheckoutField("Teléfono", phone, KeyboardType.Phone) { phone = it } }
        item { CheckoutField("Dirección", address) { address = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CheckoutProvinceSelector(state, modifier = Modifier.weight(1f)) { selected -> state = selected; city = ""; postcode = "" }
                CheckoutField("Localidad", city) { city = it }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CheckoutField("Código postal", postcode, KeyboardType.Number) { postcode = it.filter(Char::isDigit).take(5) }
                Column(Modifier.weight(1f)) {
                    Text("País", style = MaterialTheme.typography.labelMedium)
                    Text("España", modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
                }
            }
        }
        addressError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(
                onClick = {
                    addressError = validateCheckoutAddress(addressValue)
                    if (addressError == null) checkoutStore.updateCustomer(addressValue)
                },
                enabled = phase != StoreCheckoutPhase.LOADING && phase != StoreCheckoutPhase.CREATING_ORDER,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Consultar entrega") }
        }
        item { CheckoutSection(2, "Envío") }
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
                    Text(resolveShippingPresentationName(rate.methodId, rate.rateId, rate.name))
                    Text(if (rate.price.toLongOrNull() == 0L) "Gratis" else formatStorePrice(rate.price, rate.currencyMinorUnit, rate.currencySymbol), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CheckoutSection(3, "Resumen")
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (cart.items.size == 1) "1 producto" else cart.items.size.toString() + " productos",
                    style = MaterialTheme.typography.bodyLarge
                )
                cart.items.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(normalizeWooPlainText(line.name), Modifier.weight(1f))
                        Text("×" + line.quantity, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                CheckoutAmount("Subtotal", formatStorePrice(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                if (cart.coupons.isNotEmpty()) {
                    Text("Cupones aplicados", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    cart.coupons.forEach { coupon ->
                        Text("• " + resolveCouponPresentation(coupon, pendingRewards).title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val discount = cart.totals.consumerDiscount()
                if (discount.toLongOrNull()?.let { it > 0L } == true) {
                    CheckoutAmount("Descuentos", "-" + formatStorePrice(discount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                }
                val shipping = cart.totals.consumerShipping()
                CheckoutAmount(
                    "Envío",
                    when {
                        checkoutBusy -> "Calculando…"
                        selectedShipping == null -> "Pendiente"
                        shipping == null -> "Pendiente"
                        shipping == "0" -> "Gratis"
                        else -> formatStorePrice(shipping, cart.totals.currencyMinorUnit, cart.totals.currencySymbol)
                    }
                )
                CheckoutAmount("Total (IVA incluido)", formatStorePrice(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), true)
            }
        }
        item { CheckoutSection(4, "Pago") }
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


        if (createdOrder != null && (createdOrder.paymentMethod.equals("bizum", true) || createdOrder.paymentMethod.equals("cheque", true))) item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pedido recibido", style = MaterialTheme.typography.titleMedium)
                    Text("Hemos recibido tu pedido. Sigue las instrucciones de Bizum para completar el pago.")
                    Text("Pedido #${createdOrder.orderNumber ?: createdOrder.orderId ?: ""}")
                }
            }
        }
        if (paymentState == StoreCardPaymentState.PAID) item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pago confirmado", style = MaterialTheme.typography.titleMedium)
                    Text("Tu pedido se ha pagado correctamente.")
                }
            }
        }
        if (paymentState == StoreCardPaymentState.NOT_PAID) item {
            Text("El pago no se ha completado.", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    checkoutStore.clearCreatedOrder()
                    paymentStore.clearForNewProcess()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Volver a intentarlo") }
        }
        if (!error.isNullOrBlank()) item {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = {
                    if (addressValue.firstName.isNotBlank() && addressValue.lastName.isNotBlank()) checkoutStore.updateCustomer(addressValue)
                    else checkoutStore.load()
                },
                enabled = !checkoutBusy && !paymentInProgress
            ) { Text("Reintentar checkout") }
        }
        if (!paymentError.isNullOrBlank()) item {
            Text(paymentError!!, color = MaterialTheme.colorScheme.error)
            if (paymentState == StoreCardPaymentState.ERROR) {
                TextButton(onClick = paymentStore::retryReconciliation) { Text("Reintentar comprobación") }
            }
        }

        if (createdOrder != null &&
            (paymentState == StoreCardPaymentState.OPENING ||
                paymentState == StoreCardPaymentState.WAITING_RETURN ||
                paymentState == StoreCardPaymentState.RECONCILING)
        ) {
            item {
                Text("Pasarela de pago", style = MaterialTheme.typography.titleLarge)
                Text(if (paymentState == StoreCardPaymentState.RECONCILING) "Comprobando el pago…" else "Abriendo Cecabank…")
            }
        }

        item {
            Button(
                onClick = { if (canSubmit) checkoutStore.createOrder(addressValue, selectedPayment, selectedShipping!!.second) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    when {
                        phase == StoreCheckoutPhase.CREATING_ORDER -> "Creando pedido…"
                        paymentInProgress -> "Abriendo Cecabank…"
                        else -> "Pagar " + formatStorePrice(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol)
                    }
                )
            }
        }
        if (phase == StoreCheckoutPhase.CREATING_ORDER) item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Creando pedido…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CheckoutField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    CheckoutFieldFrame(label) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().height(58.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        shape = RoundedCornerShape(3.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF183B35),
            unfocusedBorderColor = Color(0xFF8A858A),
            focusedLabelColor = Color.Transparent,
            unfocusedLabelColor = Color.Transparent,
            cursorColor = Color(0xFF183B35)
        )
    )
    }
}

@Composable
private fun CheckoutProvinceSelector(selectedCode: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = CHECKOUT_SPANISH_PROVINCES.firstOrNull { it.code == selectedCode }?.name ?: "Selecciona provincia"
    Column(modifier = modifier) {
        Text("Provincia", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(3.dp)) { Text(selectedName, maxLines = 1) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CHECKOUT_SPANISH_PROVINCES.forEach { province -> androidx.compose.material3.DropdownMenuItem(text = { Text(province.name) }, onClick = { expanded = false; onSelect(province.code) }) }
        }
    }
}


@Composable
private fun CheckoutSection(number: Int, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CheckoutAmount(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (strong) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
        Text(value, fontWeight = if (strong) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}

private fun pow10(exponent: Int): Long = (1..exponent.coerceAtLeast(0)).fold(1L) { acc, _ -> acc * 10L }
