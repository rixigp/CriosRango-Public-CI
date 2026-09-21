@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.criosrango.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal
import kotlinx.coroutines.launch
import es.criosrango.shared.model.CheckoutPaymentKind
import es.criosrango.shared.model.normalizePaymentGatewayIds

private val CheckoutUiGreen = Color(0xFF183B35)
private val CheckoutUiSoftGreen = Color(0xFFE5F1ED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedesignedCheckoutScreen(
    cart: WooCart,
    checkout: CheckoutResponse?,
    loading: Boolean,
    error: String?,
    checkoutPhase: CheckoutPhase,
    onBack: () -> Unit,
    loadCheckout: (CustomerAddress) -> Unit,
    selectShipping: (Int, String) -> Unit,
    createOrder: (CustomerAddress, String, String?) -> Unit,
    deliveryAddressStore: DeliveryAddressStore,
) {
    val accountVm: AccountViewModel = viewModel()
    val accountUser by accountVm.user.collectAsStateWithLifecycle()
    val accountUserId = accountUser?.id
    val saved = remember(accountUserId) { deliveryAddressStore.load(accountUserId) }
    var firstName by remember { mutableStateOf(saved?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(saved?.lastName.orEmpty()) }
    var email by remember { mutableStateOf(saved?.email.orEmpty()) }
    var phone by remember { mutableStateOf(saved?.phone.orEmpty()) }
    var address by remember { mutableStateOf(saved?.address1.orEmpty()) }
    var postcode by remember { mutableStateOf(saved?.postcode.orEmpty()) }
    var city by remember { mutableStateOf(saved?.city.orEmpty()) }
    var province by remember { mutableStateOf(SPANISH_PROVINCES.firstOrNull { it.code == saved?.state }) }
    var country by remember { mutableStateOf(saved?.country ?: "ES") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var retryAddress by remember { mutableStateOf<CustomerAddress?>(null) }
    var lastValidAddress by remember { mutableStateOf<CustomerAddress?>(null) }
    var provinceOpen by remember { mutableStateOf(false) }
    var countryOpen by remember { mutableStateOf(false) }
    var selectedPayment by remember { mutableStateOf("") }
    var selectedShipping by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accountAddress by accountVm.address.collectAsStateWithLifecycle()
    val paymentGatewayIds = remember(checkout, cart.paymentMethods) {
        (checkout?.paymentMethods.orEmpty() + checkout?.experimentalCart?.paymentMethods.orEmpty() + cart.paymentMethods.orEmpty())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    val paymentOptions = remember(paymentGatewayIds) { normalizePaymentGatewayIds(paymentGatewayIds) }
    val paymentMethods = paymentOptions.map { it.gatewayId }
    val visibleShipping = cart.visibleShippingRatesForDestination()
    val shippingOptions = visibleShipping.flatMap { it.rates }

    LaunchedEffect(accountUserId) {
        if (accountUserId == null && deliveryAddressStore.load(null) == null) {
            firstName = ""; lastName = ""; email = ""; phone = ""
            address = ""; postcode = ""; city = ""
            province = null; country = "ES"
        }
    }
    LaunchedEffect(accountUserId, accountAddress) {
        if (accountUserId != null && AccountCartCheckoutPolicy.shouldPrefillAccountAddress(saved, accountAddress)) {
            accountAddress?.let { a ->
                firstName = a.firstName; lastName = a.lastName; email = a.email; phone = a.phone
                address = a.address1; postcode = a.postcode; city = a.city
                province = SPANISH_PROVINCES.firstOrNull { it.code.equals(a.state, true) }
                country = a.country.ifBlank { "ES" }
            }
        }
    }
    LaunchedEffect(paymentMethods) { if (selectedPayment !in paymentMethods) selectedPayment = paymentMethods.firstOrNull().orEmpty() }
    LaunchedEffect(shippingOptions.map { it.rateId to it.selected }) {
        val chosen = shippingOptions.firstOrNull { it.selected } ?: shippingOptions.firstOrNull()
        selectedShipping = chosen?.rateId
        if (chosen != null && shippingOptions.none { it.selected }) {
            visibleShipping.firstOrNull { p -> p.rates.any { it.rateId == chosen.rateId } }?.let { selectShipping(it.packageId, chosen.rateId) }
        }
    }
    LaunchedEffect(accountUserId, firstName, lastName, email, phone, address, postcode, city, province?.code, country) {
        deliveryAddressStore.save(
            CustomerAddress(firstName, lastName, email, phone, address, postcode, city, province?.code.orEmpty(), country),
            accountUserId
        )
    }

    val addressMatchesQuote = lastValidAddress?.let { a ->
        a.firstName == firstName.trim() && a.lastName == lastName.trim() && a.email == email.trim() &&
            a.phone == phone.trim() && a.address1 == address.trim() && a.postcode == postcode.trim() &&
            a.city == city.trim() && a.state == province?.code && a.country == country
    } == true
    val hasSelectedShipping = selectedShipping in shippingOptions.map { it.rateId }.filter { it.isNotBlank() }
    val hasValidTotal = cart.totals.totalPrice.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } == true
    val ready = checkoutPhase == CheckoutPhase.READY && !loading && error.isNullOrBlank() && checkout != null && addressMatchesQuote
    val canPay = !loading && error.isNullOrBlank() && lastValidAddress != null && addressMatchesQuote && checkout != null &&
        checkoutPhase == CheckoutPhase.READY && hasSelectedShipping && selectedPayment in paymentMethods && hasValidTotal

    val submit: () -> Unit = {
        val next = buildMap {
            if (firstName.isBlank()) put("Nombre", "Indica tu nombre.")
            if (lastName.isBlank()) put("Apellidos", "Indica tus apellidos.")
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) put("Email", "Introduce un email válido.")
            if (phone.isBlank()) put("Teléfono", "Indica un teléfono.")
            if (address.isBlank()) put("Dirección", "Indica una dirección.")
            if (province == null) put("Provincia", "Selecciona una provincia.")
            if (city.isBlank()) put("Localidad", "Selecciona una localidad.") else if (!isMunicipalityCompatibleWithProvince(province?.code, city)) put("Localidad", "La localidad no corresponde a la provincia seleccionada.")
            val compatible = if (province != null && city.isNotBlank()) postalCodesForMunicipality(province!!.code, city) else emptyList()
            if (!postcode.matches(Regex("\\d{5}"))) put("Código postal", "Introduce un código postal de 5 cifras.")
            else if (compatible.isNotEmpty() && postcode !in compatible) put("Código postal", "Selecciona un código postal válido para esa localidad.")
            else if (!isPostalCodeCompatibleWithProvince(province?.code, postcode)) put("Código postal", "El código postal no corresponde a la provincia seleccionada.")
            if (country != "ES") put("País", "Selecciona un país válido.")
        }
        errors = next
        val firstInvalid = listOf("Nombre","Apellidos","Email","Teléfono","Dirección","Provincia","Localidad","Código postal","País").indexOfFirst { next.containsKey(it) }
        if (firstInvalid >= 0) {
            scope.launch { listState.animateScrollToItem(firstInvalid + 1) }
        } else {
            val a = CustomerAddress(firstName.trim(), lastName.trim(), email.trim(), phone.trim(), address.trim(), postcode.trim(), city.trim(), province!!.code, country)
            lastValidAddress = a; retryAddress = a; accountVm.saveAddress(a); loadCheckout(a)
        }
        Unit
    }

    val subtotalMinor = cart.totals.consumerSubtotal().toBigDecimalOrNull() ?: BigDecimal.ZERO
    val scale = cart.totals.currencyMinorUnit
    val threshold = BigDecimal("50").movePointRight(scale)
    val remaining = (threshold - subtotalMinor).max(BigDecimal.ZERO)
    val freeMessage = if (subtotalMinor >= threshold) "¡Ya tienes envío gratis!" else "Te faltan ${formatMinorUnits(remaining.toBigInteger().toString(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol)} para conseguir envío gratis"

    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Finalizar compra", fontWeight = FontWeight.SemiBold) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
        })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE4F1ED)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalShipping,
                            contentDescription = null,
                            tint = CheckoutUiGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = freeMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CheckoutUiGreen,
                            maxLines = 1
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
            item { CheckoutSection(1, "Entrega") }
            item { CompactMainCheckoutField("Nombre", firstName, errors["Nombre"]) { firstName = it } }
            item { CompactMainCheckoutField("Apellidos", lastName, errors["Apellidos"]) { lastName = it } }
            item { CompactMainCheckoutField("Email", email, errors["Email"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) { email = it } }
            item { CompactMainCheckoutField("Teléfono", phone, errors["Teléfono"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) { phone = it } }
            item { CompactMainCheckoutField("Dirección", address, errors["Dirección"]) { address = it } }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CheckoutProvince(
                        province,
                        provinceOpen,
                        { provinceOpen = it },
                        { province = it; city = ""; postcode = ""; provinceOpen = false },
                        errors["Provincia"],
                        Modifier.weight(1f)
                    )
                    CompactCheckoutTextField(
                        "Localidad",
                        city,
                        errors["Localidad"],
                        modifier = Modifier.weight(1f)
                    ) { city = it; postcode = postalCodesForMunicipality(province?.code, it).singleOrNull().orEmpty() }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactCheckoutTextField(
                        "Código postal",
                        postcode,
                        errors["Código postal"],
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    ) { postcode = it.filter(Char::isDigit).take(5) }
                    CheckoutCountry(
                        country,
                        countryOpen,
                        { countryOpen = it },
                        { country = it; countryOpen = false },
                        errors["País"],
                        Modifier.weight(1f)
                    )
                }
            }
            item { CheckoutSection(2, "Envío") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!error.isNullOrBlank()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retryAddress?.let(loadCheckout) }, enabled = retryAddress != null) { Text("Reintentar") } }
                    Button(onClick = submit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = CheckoutUiGreen)) { Text(if (loading) "Consultando..." else "Consultar entrega") }
                    if (shippingOptions.isEmpty()) {
                        Text("Consulta la entrega para ver las opciones disponibles", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CheckoutUiGreen); Spacer(Modifier.width(10.dp)); Text("Calculando envío...", color = Color.Gray) }
                    !error.isNullOrBlank() -> Text(error, color = MaterialTheme.colorScheme.error)
                    lastValidAddress == null -> Unit
                    shippingOptions.isEmpty() -> Text("No encontramos una opción de entrega para esta dirección.", color = Color.Gray)
                    else -> Column { visibleShipping.forEach { pack -> pack.rates.forEach { rate -> Row(Modifier.fillMaxWidth().clickable { selectedShipping = rate.rateId; selectShipping(pack.packageId, rate.rateId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selectedShipping == rate.rateId, { selectedShipping = rate.rateId; selectShipping(pack.packageId, rate.rateId) }); Text(if (rate.displayShippingName().contains("CORREOS EXPRESS", true)) "CORREOS EXPRESS" else rate.displayShippingName(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(if (rate.price.toBigDecimalOrNull() == BigDecimal.ZERO) "Gratis" else formatMinorUnits(rate.price, rate.currencyMinorUnit, rate.currencySymbol), color = CheckoutUiGreen, fontWeight = FontWeight.SemiBold) } } } }
                }
            }
            item { CheckoutSection(3, "Resumen") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val productCount = cart.items.size
                    Text(
                        text = if (productCount == 1) {
                            "1 producto"
                        } else {
                            "$productCount productos"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    cart.items.forEach { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(line.name.cleanWooText(), Modifier.weight(1f)); Text("×${line.quantity}", color = Color.Gray) } }
                    HorizontalDivider()
                    CheckoutAmount("Subtotal", formatMinorUnits(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                    if (ready) {
                        CheckoutAmount("Envío", if (cart.totals.consumerShipping().toBigDecimalOrNull() == BigDecimal.ZERO) "Gratis" else formatMinorUnits(cart.totals.consumerShipping(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                        CheckoutAmount("Total (IVA incluido)", formatMinorUnits(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), true)
                    } else { CheckoutAmount("Envío", if (loading) "Calculando..." else "Pendiente"); CheckoutAmount("Total", "Pendiente", true) }
                }
            }
            item { CheckoutSection(4, "Pago") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    paymentOptions.forEach { option ->
                        val method = option.gatewayId
                        val selected = selectedPayment == method
                        val bizum = option.kind == CheckoutPaymentKind.BIZUM
                        CheckoutPaymentOption(
                            selected = selected,
                            title = if (bizum) "Bizum" else "Pago con tarjeta",
                            subtitle = if (bizum) "Recibirás las instrucciones de pago al finalizar el pedido" else "Pago seguro con tarjeta",
                            icon = if (bizum) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.CreditCard,
                            onClick = { selectedPayment = method }
                        )
                    }
                }
            }
            item {
                Button(onClick = { if (canPay) createOrder(lastValidAddress!!, selectedPayment, selectedShipping) }, enabled = canPay, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = CheckoutUiGreen)) {
                    Text(if (loading) "Procesando..." else if (ready) "Pagar ${formatMinorUnits(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}" else "Pagar", fontWeight = FontWeight.SemiBold)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CompactMainCheckoutField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            label = { Text(text = label, fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF183B35),
                unfocusedBorderColor = Color(0xFF8A858A),
                focusedLabelColor = Color(0xFF183B35),
                unfocusedLabelColor = Color(0xFF777277),
                cursorColor = Color(0xFF183B35)
            )
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompactCheckoutTextField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onChange: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(text = label, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            enabled = enabled,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(3.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF183B35),
                unfocusedBorderColor = Color(0xFF8A858A),
                focusedLabelColor = Color(0xFF183B35),
                unfocusedLabelColor = Color(0xFF777277),
                cursorColor = Color(0xFF183B35)
            )
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CheckoutPaymentOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFE4F1ED) else Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFD0C9D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = CheckoutUiGreen)
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CheckoutUiGreen,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF303030))
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF999399))
            }
        }
    }
}

@Composable private fun CheckoutSection(number: Int, title: String) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(CheckoutUiGreen), contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold) }; Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CheckoutUiGreen) } }

@Composable
private fun CheckoutProvince(
    selected: SpanishProvince?,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelected: (SpanishProvince) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selected?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .height(58.dp),
                label = { Text("Provincia", fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                isError = error != null,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF183B35),
                    unfocusedBorderColor = Color(0xFF8A858A),
                    focusedLabelColor = Color(0xFF183B35),
                    unfocusedLabelColor = Color(0xFF777277),
                    cursorColor = Color(0xFF183B35)
                ),
                shape = RoundedCornerShape(3.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpanded(false) }
            ) {
                SPANISH_PROVINCES.distinctBy { it.code }.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name) },
                        onClick = { onSelected(p) }
                    )
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CheckoutCountry(
    value: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelected: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpanded,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (value == "ES") "España" else value,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .height(58.dp),
                label = { Text("País", fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                isError = error != null,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF183B35),
                    unfocusedBorderColor = Color(0xFF8A858A),
                    focusedLabelColor = Color(0xFF183B35),
                    unfocusedLabelColor = Color(0xFF777277),
                    cursorColor = Color(0xFF183B35)
                ),
                shape = RoundedCornerShape(3.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpanded(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("España") },
                    onClick = { onSelected("ES") }
                )
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun CheckoutAmount(label: String, value: String, strong: Boolean = false) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = if (strong) CheckoutUiGreen else Color.Unspecified); Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = if (strong) CheckoutUiGreen else Color.Unspecified) } }
