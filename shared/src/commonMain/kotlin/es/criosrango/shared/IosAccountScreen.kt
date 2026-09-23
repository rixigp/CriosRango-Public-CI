package es.criosrango.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountUser
import kotlinx.coroutines.launch

private enum class IosAccountPage { HOME, LOGIN, REGISTER, FORGOT, PROFILE, ADDRESS, ORDERS, INFO, HELP, ORDER_DETAIL }

@Composable
fun CriosRangoIOSAccountScreen(repository: AccountRepository, modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf(IosAccountPage.HOME) }
    var user by remember { mutableStateOf<AccountUser?>(null) }
    var startup by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember { mutableStateOf<AccountOrderSummary?>(null) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    val scope = rememberCoroutineScope()

    fun finishAuthentication(authenticatedUser: AccountUser) {
        user = authenticatedUser
        error = null
        page = IosAccountPage.HOME
        scope.launch {
            runCatching { repository.claimPendingOrder() }
                .onFailure { println("KMP_ACCOUNT_CLAIM_FAILED=${it.message}") }
        }
    }

    LaunchedEffect(Unit) {
        println("KMP_RUNTIME_ACCOUNT_SCREEN_READY")
        println("KMP_ACCOUNT_SESSION_PRESENT=" + repository.hasSession)
        if (repository.hasSession) {
            runCatching { repository.me() }
                .onSuccess {
                    user = it
                    runCatching { repository.claimPendingOrder() }
                        .onFailure { claimError -> println("KMP_ACCOUNT_CLAIM_FAILED=${claimError.message}") }
                }
                .onFailure { error = it.message ?: "No se ha podido recuperar la sesión." }
        }
        startup = false
    }

    MaterialTheme {
        androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        if (startup) {
            FullScreenLoading("Comprobando sesión")
        } else {
            when (page) {
                IosAccountPage.HOME -> IosAccountHome(
                    user, error,
                    onLogin = { error = null; page = IosAccountPage.LOGIN },
                    onRegister = { error = null; page = IosAccountPage.REGISTER },
                    onProfile = { error = null; page = IosAccountPage.PROFILE },
                    onAddress = { error = null; page = IosAccountPage.ADDRESS },
                    onOrders = { error = null; page = IosAccountPage.ORDERS },
                    onInfoPage = { selectedInfoPage = it; error = null; page = IosAccountPage.INFO },
                    onHelp = { error = null; page = IosAccountPage.HELP },
                    onLogout = { user = null; error = null; page = IosAccountPage.HOME },
                    repository = repository
                )
                IosAccountPage.LOGIN -> IosLoginScreen(
                    repository,
                    onAuthenticated = ::finishAuthentication,
                    onRegister = { page = IosAccountPage.REGISTER },
                    onForgot = { page = IosAccountPage.FORGOT },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.REGISTER -> IosRegisterScreen(
                    repository,
                    onAuthenticated = ::finishAuthentication,
                    onLogin = { page = IosAccountPage.LOGIN },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.FORGOT -> IosForgotPasswordScreen(repository) { page = IosAccountPage.LOGIN }
                IosAccountPage.PROFILE -> IosProfileScreen(repository, user, { user = it }) { page = IosAccountPage.HOME }
                IosAccountPage.ADDRESS -> IosAddressScreen(repository) { page = IosAccountPage.HOME }
                IosAccountPage.ORDERS -> IosOrdersScreen(repository, { selectedOrder = it; page = IosAccountPage.ORDER_DETAIL }) { page = IosAccountPage.HOME }
                IosAccountPage.INFO -> selectedInfoPage?.let { infoPage -> IosInformationPageScreen(infoPage) { page = IosAccountPage.HOME } }
                IosAccountPage.HELP -> IosHelpScreen { page = IosAccountPage.HOME }
                IosAccountPage.ORDER_DETAIL -> selectedOrder?.let { IosOrderDetailScreen(it) { page = IosAccountPage.ORDERS } }
            }
        }
        }
    }
}

@Composable
private fun IosInformationPageScreen(page: AccountInfoPage, onBack: () -> Unit) {
    val client = remember { WordPressPagesClient() }
    var retryKey by remember { mutableStateOf(0) }
    var loading by remember(page, retryKey) { mutableStateOf(true) }
    var result by remember(page, retryKey) { mutableStateOf<WordPressPage?>(null) }
    var notFound by remember(page, retryKey) { mutableStateOf(false) }
    var error by remember(page, retryKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(page, retryKey) {
        loading = true; result = null; notFound = false; error = null
        runCatching { client.getPageBySlug(page.slug) }
            .onSuccess { loaded -> if (loaded == null) notFound = true else result = loaded; loading = false }
            .onFailure { error = "No se ha podido cargar esta información."; loading = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Spacer(Modifier.width(8.dp))
            Text(page.title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> FullScreenLoading("Cargando información")
            notFound -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se ha encontrado esta información.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { retryKey++ }) { Text("Reintentar") }
            }
            error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { retryKey++ }) { Text("Reintentar") }
            }
            result != null -> {
                val title = wordpressHtmlToText(result!!.title.rendered).ifBlank { page.title }
                val content = wordpressHtmlToText(result!!.content.rendered)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(title, style = MaterialTheme.typography.headlineSmall) }
                    item { if (content.isBlank()) Text("No hay contenido disponible.") else Text(content, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }

    }
}

@Composable
private fun IosHelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Spacer(Modifier.width(8.dp))
            Text("Ayuda", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(20.dp))
        Text("Contacta con nosotros", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("WhatsApp: 633 246 788")
        Text("Teléfono: 969 091 236")
        Text("Correo electrónico: criosrango@criosrango.es")
        Spacer(Modifier.height(20.dp))
        Text("Horario de atención", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Text("Lunes a viernes: 10:00–13:30 · 17:30–21:00")
        Text("Sábado: 10:00–13:30")
        Text("Domingo: Cerrado")
    }
}

@Composable
private fun IosAccountHome(
    user: AccountUser?,
    error: String?,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onProfile: () -> Unit,
    onAddress: () -> Unit,
    onOrders: () -> Unit,
    onInfoPage: (AccountInfoPage) -> Unit,
    onHelp: () -> Unit,
    onLogout: () -> Unit,
    repository: AccountRepository
) {
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Críos&Rango", style = MaterialTheme.typography.headlineMedium)
        Text("Cuenta", style = MaterialTheme.typography.titleLarge)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (user == null) {
            Text("Accede a tu cuenta para consultar tus datos, dirección y pedidos.")
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
            Button(onClick = { onInfoPage(AccountInfoPage.RETURNS) }, modifier = Modifier.fillMaxWidth()) { Text("Cambios y devoluciones") }
            Button(onClick = { onInfoPage(AccountInfoPage.TERMS) }, modifier = Modifier.fillMaxWidth()) { Text("Condiciones de contratación") }
            Button(onClick = { onInfoPage(AccountInfoPage.PRIVACY) }, modifier = Modifier.fillMaxWidth()) { Text("Política de privacidad") }
            Button(onClick = { onInfoPage(AccountInfoPage.LEGAL) }, modifier = Modifier.fillMaxWidth()) { Text("Aviso legal") }
            Button(onClick = { onInfoPage(AccountInfoPage.COOKIES) }, modifier = Modifier.fillMaxWidth()) { Text("Política de cookies") }
            Button(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text("Ayuda") }
        } else {
            Text("Hola, " + user.displayName.ifBlank { user.email })
            Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Mi perfil") }
            Button(onClick = onAddress, modifier = Modifier.fillMaxWidth()) { Text("Mi dirección") }
            Button(onClick = onOrders, modifier = Modifier.fillMaxWidth()) { Text("Mis pedidos") }
            Button(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text("Ayuda") }
            Button(onClick = { onInfoPage(AccountInfoPage.RETURNS) }, modifier = Modifier.fillMaxWidth()) { Text("Cambios y devoluciones") }
            Button(onClick = { onInfoPage(AccountInfoPage.TERMS) }, modifier = Modifier.fillMaxWidth()) { Text("Condiciones de contratación") }
            Button(onClick = { onInfoPage(AccountInfoPage.PRIVACY) }, modifier = Modifier.fillMaxWidth()) { Text("Política de privacidad") }
            Button(onClick = { onInfoPage(AccountInfoPage.LEGAL) }, modifier = Modifier.fillMaxWidth()) { Text("Aviso legal") }
            Button(onClick = { onInfoPage(AccountInfoPage.COOKIES) }, modifier = Modifier.fillMaxWidth()) { Text("Política de cookies") }
            OutlinedButton(
                enabled = !loggingOut,
                onClick = {
                    loggingOut = true
                    scope.launch {
                        runCatching { repository.logout() }
                        loggingOut = false
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loggingOut) "Cerrando sesión…" else "Cerrar sesión") }
        }
    }
}

@Composable
private fun IosLoginScreen(
    repository: AccountRepository,
    onAuthenticated: (AccountUser) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AccountForm("Iniciar sesión", onBack) {
        OutlinedTextField(login, { login = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && login.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repository.login(login, password) }
                        .onSuccess(onAuthenticated)
                        .onFailure { error = it.message ?: "No se ha podido iniciar sesión." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Entrando…" else "Iniciar sesión") }
        TextButton(onClick = onForgot) { Text("He olvidado mi contraseña") }
        TextButton(onClick = onRegister) { Text("Crear una cuenta") }
    }
}

@Composable
private fun IosRegisterScreen(
    repository: AccountRepository,
    onAuthenticated: (AccountUser) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AccountForm("Crear cuenta", onBack) {
        OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repository.register(email, password, firstName, lastName, phone) }
                        .onSuccess(onAuthenticated)
                        .onFailure { error = it.message ?: "No se ha podido crear la cuenta." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Creando…" else "Crear cuenta") }
        TextButton(onClick = onLogin) { Text("Ya tengo una cuenta") }
    }
}

@Composable
private fun IosForgotPasswordScreen(repository: AccountRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AccountForm("Recuperar contraseña", onBack) {
        OutlinedTextField(login, { login = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        message?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && login.isNotBlank(),
            onClick = {
                busy = true; message = null; error = null
                scope.launch {
                    runCatching { repository.forgotPassword(login) }
                        .onSuccess { message = it.ifBlank { "Revisa tu correo para continuar." } }
                        .onFailure { error = it.message ?: "No se ha podido solicitar la recuperación." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Enviando…" else "Enviar recuperación") }
    }
}

@Composable
private fun IosProfileScreen(
    repository: AccountRepository,
    user: AccountUser?,
    onUserChanged: (AccountUser) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var firstName by remember { mutableStateOf(user?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(user?.lastName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AccountForm("Mi perfil", onBack) {
        Text("Email: " + user?.email.orEmpty())
        OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, modifier = Modifier.fillMaxWidth())
        notice?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy,
            onClick = {
                busy = true; error = null; notice = null
                scope.launch {
                    runCatching {
                        val current = repository.customerAddress()
                        repository.saveCustomerAddress(current.copy(firstName = firstName.trim(), lastName = lastName.trim()))
                        repository.me()
                    }.onSuccess {
                        onUserChanged(it)
                        notice = "Datos actualizados"
                    }.onFailure { error = it.message ?: "No se han podido guardar los datos." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Guardando…" else "Guardar cambios") }
    }
}

@Composable
private fun IosAddressScreen(repository: AccountRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(AccountCustomerAddress()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { repository.customerAddress() }
            .onSuccess { address = it; loaded = true }
            .onFailure { error = it.message ?: "No se ha podido cargar la dirección."; loaded = true }
    }
    AccountForm("Mi dirección", onBack) {
        if (!loaded) {
            FullScreenLoading("Cargando dirección")
        } else {
            AccountAddressFields(address) { address = it }
            notice?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; error = null; notice = null
                    scope.launch {
                        runCatching { repository.saveCustomerAddress(address) }
                            .onSuccess { notice = "Dirección actualizada" }
                            .onFailure { error = it.message ?: "No se ha podido guardar la dirección." }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Guardando…" else "Guardar dirección") }
        }
    }
}

@Composable
private fun AccountAddressFields(address: AccountCustomerAddress, onChange: (AccountCustomerAddress) -> Unit) {
    val fields = listOf(
        "Nombre" to address.firstName, "Apellidos" to address.lastName, "Teléfono" to address.phone,
        "Dirección" to address.address1, "Dirección 2" to address.address2, "Código postal" to address.postcode,
        "Ciudad" to address.city, "Provincia" to address.state, "País" to address.country
    )
    fields.forEach { (label, value) ->
        OutlinedTextField(
            value, { newValue ->
                onChange(
                    when (label) {
                        "Nombre" -> address.copy(firstName = newValue)
                        "Apellidos" -> address.copy(lastName = newValue)
                        "Teléfono" -> address.copy(phone = newValue)
                        "Dirección" -> address.copy(address1 = newValue)
                        "Dirección 2" -> address.copy(address2 = newValue)
                        "Código postal" -> address.copy(postcode = newValue)
                        "Ciudad" -> address.copy(city = newValue)
                        "Provincia" -> address.copy(state = newValue)
                        "País" -> address.copy(country = newValue)
                        else -> address
                    }
                )
            },
            label = { Text(label) }, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IosOrdersScreen(
    repository: AccountRepository,
    onOpenOrder: (AccountOrderSummary) -> Unit,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf(emptyList<AccountOrderSummary>()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun retry() { loading = true }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { repository.orders().orders }
            .onSuccess { orders = it; loading = false }
            .onFailure { error = it.message ?: "No se han podido cargar tus pedidos."; loading = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Spacer(Modifier.width(8.dp))
            Text("Mis pedidos", style = MaterialTheme.typography.titleLarge)
        }
        when {
            loading -> FullScreenLoading("Cargando pedidos")
            error != null -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { error = null; retry() }) { Text("Reintentar") }
            }
            orders.isEmpty() -> Text("No tienes pedidos.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(orders, key = { it.id }) { order ->
                    Button(onClick = { onOpenOrder(order) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text("Pedido #" + order.number)
                            Text(order.statusLabel.ifBlank { order.status })
                            Text("Total: " + order.total + " " + order.currency)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun IosOrderDetailScreen(order: AccountOrderSummary, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Pedido #" + order.number, style = MaterialTheme.typography.headlineSmall)
            Text(order.statusLabel.ifBlank { order.status })
            order.dateCreated?.takeIf { it.isNotBlank() }?.let { Text("Fecha: " + it.take(10)) }
            Spacer(Modifier.height(8.dp))
            Text("Productos", style = MaterialTheme.typography.titleMedium)
        }
        items(order.items) { item ->
            Text(item.quantity.toString() + " × " + item.name)
            item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }.forEach {
                Text(it.name + ": " + it.value, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Pago: " + order.paymentMethodTitle.ifBlank { order.paymentMethod })
            Text("Subtotal: " + order.subtotal + " " + order.currency)
            Text("Envío: " + order.shippingTotal + " " + order.currency)
            Text("Total: " + order.total + " " + order.currency, style = MaterialTheme.typography.titleLarge)
            val a = order.shippingAddress
            if (a.address1.isNotBlank() || a.city.isNotBlank() || a.postcode.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Entrega", style = MaterialTheme.typography.titleMedium)
                if (a.address1.isNotBlank()) Text(a.address1)
                val location = listOf(a.postcode, a.city, a.state).filter { it.isNotBlank() }.joinToString(" · ")
                if (location.isNotBlank()) Text(location)
                if (a.country.isNotBlank()) Text(a.country)
            }
        }
    }
}

@Composable
private fun AccountForm(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun FullScreenLoading(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text)
    }
}
