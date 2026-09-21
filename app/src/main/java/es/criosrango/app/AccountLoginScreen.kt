package es.criosrango.app

import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountUser

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AccountLoginScreen(
    padding: PaddingValues,
    vm: AccountViewModel = viewModel(),
    onAuthenticated: (() -> Unit)? = null
) {
    val authState by vm.authState.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val accountError by vm.accountError.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val address by vm.address.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    val currentUser = user
    var accountSection by remember(currentUser?.id) { mutableStateOf(AccountSection.HOME) }
    var selectedOrderId by remember { mutableStateOf<Int?>(null) }
    val selectedOrder = orders.firstOrNull { it.id == selectedOrderId }

    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            showLogin = false
            vm.refreshOrders()
            onAuthenticated?.invoke()
        }
    }

    if (currentUser == null && accountError?.type == StoreErrorType.SESSION_EXPIRED) {
        StoreErrorState(accountError!!, padding, onLogin = { vm.clearAccountMessages(); showLogin = true })
        return
    }

    if (authState == AccountAuthState.CHECKING && accountError != null) {
        StoreErrorState(accountError!!, padding, vm::retrySession)
        return
    }

    if (authState == AccountAuthState.CHECKING) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
        return
    }

    if (selectedInfoPage != null && selectedOrder == null) {
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            AccountInformationPageContent(selectedInfoPage!!, onBack = { selectedInfoPage = null; accountSection = AccountSection.HOME })
        }
        return
    }

    BackHandler(enabled = selectedOrder != null) { selectedOrderId = null }
    BackHandler(enabled = selectedOrder == null && !showLogin && accountSection != AccountSection.HOME) {
        accountSection = when (accountSection) {
            AccountSection.DATA, AccountSection.ADDRESSES -> AccountSection.PROFILE
            AccountSection.PROFILE, AccountSection.ORDERS, AccountSection.HELP -> AccountSection.HOME
            AccountSection.HOME -> AccountSection.HOME
        }
    }
    BackHandler(enabled = currentUser == null && showLogin && selectedOrder == null) {
        showLogin = false
        showRegister = false
        showForgot = false
        vm.clearAccountMessages()
    }

    if (currentUser != null && selectedOrder != null) {
        AccountOrderDetailScreenV2(padding, selectedOrder) { selectedOrderId = null }
        return
    }

    if (currentUser != null) {
        val fullName = listOf(currentUser.firstName, currentUser.lastName).filter { it.isNotBlank() }.joinToString(" ")
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            when (accountSection) {
                AccountSection.HOME -> AccountHomeContentV2(
                    fullName = fullName,
                    email = currentUser.email,
                    onOrders = { accountSection = AccountSection.ORDERS },
                    onProfile = { accountSection = AccountSection.PROFILE },
                    onLogin = { showLogin = true },
                    onHelp = { accountSection = AccountSection.HELP },
                    onInfoPage = { selectedInfoPage = it }
                )
                AccountSection.ORDERS -> AccountOrdersContent(orders, loading, accountError, { accountSection = AccountSection.HOME }, { selectedOrderId = it }, vm::refreshOrders)
                AccountSection.PROFILE -> AccountProfileContent(loading, { accountSection = AccountSection.HOME }, { accountSection = AccountSection.DATA }, { accountSection = AccountSection.ADDRESSES }, { vm.clearAccountMessages(); showForgot = true }, vm::logout)
                AccountSection.DATA -> AccountPersonalDataContent(vm, currentUser) { accountSection = AccountSection.PROFILE }
                AccountSection.ADDRESSES -> AccountAddressContent(vm, address) { accountSection = AccountSection.PROFILE }
                AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (showForgot) AccountForgotPasswordDialog(login, loading, error, notice, { showForgot = false; vm.clearAccountMessages() }, vm::forgotPassword)
    } else if (!showLogin) {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            when (accountSection) {
                AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
                else -> AccountHomeContentV2(
                    fullName = null,
                    email = null,
                    onOrders = { showLogin = true },
                    onProfile = { showLogin = true },
                    onLogin = { showLogin = true },
                    onHelp = { accountSection = AccountSection.HELP },
                    onInfoPage = { selectedInfoPage = it }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(login, { login = it }, label = { Text("Correo o usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button({ vm.login(login.trim(), password) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Iniciar sesión") }
            Spacer(Modifier.height(8.dp))
            TextButton({ vm.clearAccountMessages(); showForgot = true }, modifier = Modifier.fillMaxWidth()) { Text("He olvidado mi contraseña") }
            OutlinedButton({ vm.clearAccountMessages(); showRegister = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
            if (showRegister) AccountRegisterDialog(login, loading, error, { showRegister = false; vm.clearAccountMessages() }, vm::createAccount)
            if (showForgot) AccountForgotPasswordDialog(login, loading, error, notice, { showForgot = false; vm.clearAccountMessages() }, vm::forgotPassword)
        }
    }
}

private enum class AccountSection { HOME, ORDERS, PROFILE, DATA, ADDRESSES, HELP }

@Composable
private fun AccountCompactAccess(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFE3EFEB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF183B35),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AccountHomeContentV2(
    fullName: String?,
    email: String?,
    onOrders: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit,
    onInfoPage: (AccountInfoPage) -> Unit
) {
    val context = LocalContext.current
    val isAuthenticated = !fullName.isNullOrBlank() && !email.isNullOrBlank()
    val nameParts = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = buildString { nameParts.firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }; nameParts.drop(1).firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) } }.ifBlank { "CR" }
    Column(Modifier.fillMaxWidth()) {
        Text("Cuenta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF183B35))
        Spacer(Modifier.height(20.dp))
        if (isAuthenticated) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(60.dp), shape = CircleShape, color = Color(0xFFE9E6EA)) { Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35)) } }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { Text(fullName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Spacer(Modifier.height(3.dp)); Text(email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLogin).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(60.dp).background(Color(0xFFE3EFEB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Inicia sesión o regístrate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text("Accede a tu cuenta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(if (isAuthenticated) 24.dp else 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountCompactAccess(Modifier.weight(1f), "Pedidos", Icons.Outlined.History, onOrders)
            AccountCompactAccess(Modifier.weight(1f), "Cupones", Icons.Outlined.Sell) { android.widget.Toast.makeText(context, "Próximamente", android.widget.Toast.LENGTH_SHORT).show() }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountCompactAccess(Modifier.weight(1f), "Perfil", Icons.Outlined.Person, onProfile)
            AccountCompactAccess(Modifier.weight(1f), "Ayuda", Icons.AutoMirrored.Outlined.HelpOutline, onHelp)
        }
        Spacer(Modifier.height(28.dp))
        AccountInformationHomeSection(onInfoPage)
        Spacer(Modifier.height(20.dp))
        Text("Versión ${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountHelpContent(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        AccountSectionHeader("Ayuda", onBack)
        Spacer(Modifier.height(22.dp))
        Text("Contacta con nosotros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35))
        Spacer(Modifier.height(16.dp))
        HelpContactRow("WhatsApp", "633 246 788", Icons.AutoMirrored.Outlined.Chat) {
            val message = "Hola, necesito ayuda con la app de Críos&Rango."
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val whatsappUri = Uri.parse("https://wa.me/34633246788?text=$encodedMessage")
            val intent = Intent(Intent.ACTION_VIEW, whatsappUri)
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir WhatsApp.", Toast.LENGTH_SHORT).show()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HelpContactRow("Llamar", "969 091 236", Icons.Outlined.Phone) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+34969091236"))
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir el teléfono.", Toast.LENGTH_SHORT).show()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HelpContactRow("Correo electrónico", "criosrango@criosrango.es", Icons.Outlined.Email) {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:criosrango@criosrango.es")
                putExtra(Intent.EXTRA_SUBJECT, "Consulta desde la app Críos&Rango")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir el correo.", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(28.dp))
        StoreHoursSection()
    }
}

@Composable
private fun StoreHoursSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Horario de atención",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF183B35)
        )
        Spacer(Modifier.height(14.dp))
        StoreHoursRow("Lunes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Martes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Miércoles", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Jueves", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Viernes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Sábado", "10:00–13:30")
        StoreHoursRow("Domingo", "Cerrado")
    }
}

@Composable
private fun StoreHoursRow(day: String, hours: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = day,
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = hours,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpContactRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(Color(0xFFE3EFEB), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountOrdersContent(orders: List<AccountOrderSummary>, loading: Boolean, accountError: StoreUiError?, onBack: () -> Unit, onOrderClick: (Int) -> Unit, onRefresh: () -> Unit) {
    AccountSectionHeader("Pedidos", onBack); Spacer(Modifier.height(16.dp))
    if (loading && orders.isEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
    else if (accountError != null && accountError.type != StoreErrorType.SESSION_EXPIRED && orders.isEmpty()) StoreErrorState(accountError, PaddingValues(0.dp), onRefresh)
    else if (orders.isEmpty()) Text("Todavía no tienes pedidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (accountError != null && accountError.type != StoreErrorType.SESSION_EXPIRED) {
        Text(accountError.title, color = MaterialTheme.colorScheme.error)
        Text(accountError.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRefresh, enabled = !loading) { Text("Reintentar") }
    }
    else orders.forEach { order ->
        Card(Modifier.fillMaxWidth().clickable { onOrderClick(order.id) }) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Pedido #${order.number.ifBlank { order.id.toString() }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); OrderStatusBadge(order.status, order.statusLabel) }
                order.dateCreated?.takeIf { it.isNotBlank() }?.let { rawDate -> val p = rawDate.take(10).split("-"); Spacer(Modifier.height(8.dp)); Text("Fecha: ${if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else rawDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (order.items.isNotEmpty()) { Spacer(Modifier.height(10.dp)); order.items.forEach { item -> Text("${item.quantity} × ${item.name}"); val variationText = item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }.joinToString(" · ") { "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${it.value}" }; if (variationText.isNotBlank()) Text(variationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(5.dp)) } }
                if (order.paymentMethodTitle.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta") }
                if (order.total.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(if (order.currency == "EUR") "Total: ${order.total.replace('.', ',')} €" else "${order.total} ${order.currency}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (accountError == null || accountError.type == StoreErrorType.SESSION_EXPIRED) {
        Spacer(Modifier.height(12.dp)); OutlinedButton(onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Actualizar pedidos") }
    }
}

@Composable
private fun AccountProfileContent(loading: Boolean, onBack: () -> Unit, onPersonalData: () -> Unit, onAddress: () -> Unit, onPassword: () -> Unit, onLogout: () -> Unit) {
    AccountSectionHeader("Perfil", onBack); Spacer(Modifier.height(22.dp)); ProfileMenuRow("Datos personales", Icons.Outlined.Person, onPersonalData); Spacer(Modifier.height(12.dp)); ProfileMenuRow("Dirección de entrega", Icons.Outlined.LocationOn, onAddress); Spacer(Modifier.height(12.dp)); ProfileMenuRow("Cambiar contraseña", Icons.Outlined.Lock, onPassword); Spacer(Modifier.height(22.dp))
    Card(Modifier.fillMaxWidth().clickable(enabled = !loading, onClick = onLogout), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECEF))) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Color(0xFFC73B4C)); Spacer(Modifier.width(14.dp)); Text("Cerrar sesión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color(0xFFC73B4C)) } }
}

@Composable
private fun ProfileMenuRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EDF1))) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium); Text("›", style = MaterialTheme.typography.titleLarge) } }
}

@Composable
private fun AccountPersonalDataContent(vm: AccountViewModel, user: AccountUser, onBack: () -> Unit) {
    val saving by vm.savingAccountDetails.collectAsStateWithLifecycle(); val saveError by vm.accountDataError.collectAsStateWithLifecycle(); val saveNotice by vm.accountDataNotice.collectAsStateWithLifecycle(); var firstName by remember(user.id) { mutableStateOf(user.firstName) }; var lastName by remember(user.id) { mutableStateOf(user.lastName) }
    AccountSectionHeader("Datos personales", onBack); Spacer(Modifier.height(20.dp)); OutlinedTextField(firstName, { firstName = it; vm.clearAccountDataMessages() }, label = { Text("Nombre") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(lastName, { lastName = it; vm.clearAccountDataMessages() }, label = { Text("Apellidos") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(user.email.ifBlank { "—" }, {}, label = { Text("Correo electrónico") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.updateAccountDetails(firstName.trim(), lastName.trim()) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar cambios") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFF183B35)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAddressContent(vm: AccountViewModel, address: AccountCustomerAddress?, onBack: () -> Unit) {
    val saving by vm.savingAddress.collectAsStateWithLifecycle(); val saveError by vm.addressSaveError.collectAsStateWithLifecycle(); val saveNotice by vm.addressSaveNotice.collectAsStateWithLifecycle(); var firstName by remember(address) { mutableStateOf(address?.firstName.orEmpty()) }; var lastName by remember(address) { mutableStateOf(address?.lastName.orEmpty()) }; var address1 by remember(address) { mutableStateOf(address?.address1.orEmpty()) }; var address2 by remember(address) { mutableStateOf(address?.address2.orEmpty()) }; var postcode by remember(address) { mutableStateOf(address?.postcode.orEmpty()) }; var city by remember(address) { mutableStateOf(address?.city.orEmpty()) }; var state by remember(address) { mutableStateOf(address?.state.orEmpty()) }; var provinceExpanded by remember { mutableStateOf(false) }; val provinceName = SPANISH_PROVINCES.firstOrNull { it.code.equals(state, true) }?.name.orEmpty()
    AccountSectionHeader("Dirección de entrega", onBack); Spacer(Modifier.height(20.dp)); AccountAddressField("Nombre", firstName, !saving) { firstName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Apellidos", lastName, !saving) { lastName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección", address1, !saving) { address1 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección adicional / Piso, puerta, etc. (opcional)", address2, !saving) { address2 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Código postal", postcode, !saving) { postcode = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Localidad", city, !saving) { city = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { if (!saving) provinceExpanded = it }) { OutlinedTextField(provinceName.ifBlank { state }, {}, label = { Text("Provincia") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !saving).fillMaxWidth()); ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) { SPANISH_PROVINCES.distinctBy { it.code }.forEach { province -> DropdownMenuItem(text = { Text(province.name) }, onClick = { state = province.code; provinceExpanded = false; vm.clearAddressSaveMessages() }) } } }; Spacer(Modifier.height(14.dp)); OutlinedTextField("España", {}, label = { Text("País") }, readOnly = true, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.saveAddress(AccountCustomerAddress(firstName = firstName, lastName = lastName, email = address?.email.orEmpty(), phone = address?.phone.orEmpty(), address1 = address1, address2 = address2, postcode = postcode, city = city, state = state, country = "ES")) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar dirección") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it) }
}

@Composable
private fun AccountAddressField(label: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) { OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth()) }

@Composable
fun AccountSectionHeader(title: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) { Text("←") }; Spacer(Modifier.width(6.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF183B35)) } }

@Composable
private fun AccountRegisterDialog(initialEmail: String, loading: Boolean, error: String?, onDismiss: () -> Unit, onCreate: (String, String, String, String, String) -> Unit) {
    var firstName by remember { mutableStateOf("") }; var lastName by remember { mutableStateOf("") }; var email by remember(initialEmail) { mutableStateOf(initialEmail) }; var phone by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var repeatPassword by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }, title = { Text("Crear cuenta") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono (opcional)") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(repeatPassword, { repeatPassword = it }, label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); if (repeatPassword.isNotEmpty() && password != repeatPassword) { Spacer(Modifier.height(8.dp)); Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error) }; error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton({ onCreate(firstName.trim(), lastName.trim(), email.trim(), phone.trim(), password) }, enabled = !loading && firstName.isNotBlank() && lastName.isNotBlank() && email.isNotBlank() && password.length >= 8 && password == repeatPassword) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crear cuenta") } }, dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } })
}

@Composable
private fun AccountForgotPasswordDialog(initialLogin: String, loading: Boolean, error: String?, notice: String?, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var login by remember(initialLogin) { mutableStateOf(initialLogin) }
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }, title = { Text("Recuperar contraseña") }, text = { Column { Text("Introduce tu correo o nombre de usuario."); Spacer(Modifier.height(12.dp)); OutlinedTextField(login, { login = it }, label = { Text("Correo o usuario") }, singleLine = true); error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; notice?.let { Spacer(Modifier.height(8.dp)); Text(it) } } }, confirmButton = { if (notice == null) TextButton({ onSend(login.trim()) }, enabled = !loading && login.isNotBlank()) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enviar correo") } else TextButton(onDismiss) { Text("Cerrar") } }, dismissButton = { if (notice == null) TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } })
}