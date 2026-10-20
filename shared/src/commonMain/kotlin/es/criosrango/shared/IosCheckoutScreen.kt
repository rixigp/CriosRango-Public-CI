package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalShipping
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

private val CheckoutUiGreen = Color(0xFF183B35)
private val CheckoutUiSoftGreen = Color(0xFFE4F1ED)
private val CheckoutUiBorder = Color(0xFFD0C9D0)

@Composable
fun StoreCheckoutScreen(
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    accountRepository: AccountRepository,
    initialAddress: CustomerAddress? = null,
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
    val stableCreatedOrder = createdOrder
    val paymentState by paymentStore.state.collectAsState()
    val paymentError by paymentStore.error.collectAsState()
    var pendingRewards by remember { mutableStateOf<List<LoyaltyReward>>(emptyList()) }
    LaunchedEffect(walletEnabled) {
        pendingRewards = if (walletEnabled) runCatching { loyaltyRepository.getWallet().pendingRewards }.getOrDefault(emptyList()) else emptyList()
    }

    // Seed from any address already held by the store or the platform account adapter.
    // This keeps known customer data visible while the checkout quote is loading.
    val knownAddress = accountAddress ?: initialAddress
    var firstName by remember { mutableStateOf(knownAddress?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(knownAddress?.lastName.orEmpty()) }
    var email by remember { mutableStateOf(knownAddress?.email.orEmpty()) }
    var phone by remember { mutableStateOf(knownAddress?.phone.orEmpty()) }
    var address by remember { mutableStateOf(knownAddress?.address1.orEmpty()) }
    var address2 by remember { mutableStateOf(knownAddress?.address2.orEmpty()) }
    var postcode by remember { mutableStateOf(knownAddress?.postcode.orEmpty()) }
    var city by remember { mutableStateOf(knownAddress?.city.orEmpty()) }
    var state by remember { mutableStateOf(knownAddress?.state.orEmpty()) }
    var country by remember { mutableStateOf(knownAddress?.country?.ifBlank { "ES" } ?: "ES") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var selectedPayment by remember { mutableStateOf("") }
    var selectedShipping by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var countryOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { checkoutStore.load() }
    LaunchedEffect(accountAddress, initialAddress) {
        (accountAddress ?: initialAddress)?.let {
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

    Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
        StoreGlobalTopBar(cartQuantity = cartQuantity, onSearch = onSearch, onCart = onCart)
        StoreCatalogHeader(title = "Finalizar compra", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = CheckoutUiSoftGreen
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.LocalShipping,
                        contentDescription = null,
                        tint = CheckoutUiGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = freeShippingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CheckoutUiGreen
                    )
                }
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
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CheckoutUiGreen, contentColor = Color.White)) { Text("Volver al carrito") }
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
                CheckoutField("Localidad", city, modifier = Modifier.weight(1f)) { city = it }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CheckoutField("Código postal", postcode, KeyboardType.Number, modifier = Modifier.weight(1f)) { postcode = it.filter(Char::isDigit).take(5) }
                CheckoutCountry(
                    value = country,
                    expanded = countryOpen,
                    onExpandedChange = { countryOpen = it },
                    onSelected = { country = it; countryOpen = false },
                    error = if (country == "ES") null else "Selecciona un país válido.",
                    modifier = Modifier.weight(1f)
                )
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CheckoutUiGreen, contentColor = Color.White)
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
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedShipping == (pack.packageId to rate.rateId),
                    onClick = {
                        selectedShipping = pack.packageId to rate.rateId
                        checkoutStore.selectShipping(pack.packageId, rate.rateId)
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = CheckoutUiGreen, unselectedColor = Color(0xFF777277))
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
        item {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                paymentOptions.forEach { option ->
                    val bizum = option.kind == es.criosrango.shared.model.CheckoutPaymentKind.BIZUM
                    CheckoutPaymentOption(
                        selected = selectedPayment == option.gatewayId,
                        title = if (bizum) "Bizum" else "Pago con tarjeta",
                        subtitle = if (bizum) "Recibirás las instrucciones de pago al finalizar el pedido" else "Pago seguro con tarjeta",
                        icon = if (bizum) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.CreditCard,
                        onClick = { selectedPayment = option.gatewayId }
                    )
                }
            }
        }


        if (stableCreatedOrder != null && (stableCreatedOrder.paymentMethod.equals("bizum", true) || stableCreatedOrder.paymentMethod.equals("cheque", true))) item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = CheckoutUiSoftGreen) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pedido recibido", style = MaterialTheme.typography.titleMedium)
                    Text("Hemos recibido tu pedido. Sigue las instrucciones de Bizum para completar el pago.")
                    Text("Pedido #${stableCreatedOrder.orderNumber ?: stableCreatedOrder.orderId ?: ""}")
                }
            }
        }
        if (paymentState == StoreCardPaymentState.PAID) item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = CheckoutUiSoftGreen) {
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CheckoutUiGreen, contentColor = Color.White)
            ) { Text("Volver a intentarlo") }
        }
        if (!error.isNullOrBlank()) item {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = {
                    if (addressValue.firstName.isNotBlank() && addressValue.lastName.isNotBlank()) checkoutStore.updateCustomer(addressValue)
                    else checkoutStore.load()
                },
                enabled = !checkoutBusy && !paymentInProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = CheckoutUiGreen)
            ) { Text("Reintentar checkout") }
        }
        if (!paymentError.isNullOrBlank()) item {
            Text(paymentError!!, color = MaterialTheme.colorScheme.error)
            if (paymentState == StoreCardPaymentState.ERROR) {
                TextButton(onClick = paymentStore::retryReconciliation, colors = ButtonDefaults.textButtonColors(contentColor = CheckoutUiGreen)) { Text("Reintentar comprobación") }
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
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CheckoutUiGreen,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE0E0E0),
                    disabledContentColor = Color(0xFF777777)
                )
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
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CheckoutUiGreen)
                Spacer(Modifier.width(10.dp))
                Text("Creando pedido…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

@Composable
private fun CheckoutField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().height(58.dp),
        label = { Text(label, fontSize = 13.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
        singleLine = true,
        colors = checkoutFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutProvinceSelector(selectedCode: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = CHECKOUT_SPANISH_PROVINCES.firstOrNull { it.code == selectedCode }?.name.orEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                label = { Text("Provincia", fontSize = 13.sp) }, value = selectedName, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth().height(58.dp),
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = checkoutFieldColors(), shape = RoundedCornerShape(3.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CHECKOUT_SPANISH_PROVINCES.distinctBy { it.code }.forEach { province ->
                    DropdownMenuItem(text = { Text(province.name) }, onClick = { expanded = false; onSelect(province.code) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutCountry(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                label = { Text("País", fontSize = 13.sp) }, value = if (value == "ES") "España" else value,
                onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth().height(58.dp),
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, isError = error != null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = checkoutFieldColors(), shape = RoundedCornerShape(3.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                DropdownMenuItem(text = { Text("España") }, onClick = { onSelected("ES") })
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun checkoutFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF183B35), unfocusedBorderColor = Color(0xFF8A858A),
    focusedLabelColor = Color(0xFF183B35), unfocusedLabelColor = Color(0xFF777277), cursorColor = Color(0xFF183B35)
)


@Composable
private fun CheckoutPaymentOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick
        ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) CheckoutUiSoftGreen else Color.Transparent,
        border = BorderStroke(1.dp, CheckoutUiBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CheckoutUiGreen,
                    unselectedColor = Color(0xFF777277)
                )
            )
            Spacer(Modifier.width(14.dp))
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CheckoutUiGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF303030))
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF999399))
            }
        }
    }
}

@Composable
private fun CheckoutSection(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(CheckoutUiGreen), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CheckoutUiGreen)
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
