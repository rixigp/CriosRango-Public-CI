package es.criosrango.shared

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val SPANISH_PROVINCE_CODES = setOf("C","VI","AB","A","AL","O","AV","BA","B","BI","BU","CC","CA","S","CS","CE","CR","CO","CU","GI","GR","GU","SS","H","HU","J","LE","L","LO","LU","M","MA","ML","MU","NA","OR","P","GC","PO","SA","TF","SG","SE","SO","T","TE","TO","V","VA","ZA","Z")

private enum class IosAccountPage { HOME, LOGIN, REGISTER, FORGOT, PROFILE, DATA, ADDRESS, ORDERS, INFO, HELP, ORDER_DETAIL }

@Composable
fun CriosRangoIOSAccountScreen(
    repository: AccountRepository,
    modifier: Modifier = Modifier,
    onOpenExternalUrl: (String) -> Unit,
    initialOrderId: Int? = null
) {
    var page by remember { mutableStateOf(IosAccountPage.HOME) }
    var user by remember { mutableStateOf<AccountUser?>(null) }
    var startup by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember { mutableStateOf<AccountOrderSummary?>(null) }
    var pendingPushOrderId by remember { mutableStateOf(initialOrderId) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    var forgotReturnPage by remember { mutableStateOf(IosAccountPage.LOGIN) }
    var addressReturnPage by remember { mutableStateOf(IosAccountPage.HOME) }
    val scope = rememberCoroutineScope()
    fun invalidateExpiredSession() { repository.clearLocalSession(); user = null; selectedOrder = null; error = null; page = IosAccountPage.HOME }

    LaunchedEffect(user?.id, pendingPushOrderId) {
        val id = pendingPushOrderId ?: return@LaunchedEffect
        if (user == null) return@LaunchedEffect
        selectedOrder = runCatching { repository.orders().orders }.getOrNull()?.firstOrNull { it.id == id }
        if (selectedOrder != null) page = IosAccountPage.ORDER_DETAIL
        pendingPushOrderId = null
    }

    fun finishAuthentication(authenticatedUser: AccountUser) {
        user = authenticatedUser
        error = null
        page = IosAccountPage.HOME
        scope.launch {
            runCatching { repository.claimPendingOrder() }
                .onFailure { println("KMP_ACCOUNT_CLAIM_FAILED=${it.message}") }
            if (!repository.hasSession) {
                user = null
                error = "La sesión ha caducado. Vuelve a iniciar sesión."
                page = IosAccountPage.HOME
            }
        }
    }

    LaunchedEffect(Unit) {
        println("KMP_RUNTIME_ACCOUNT_SCREEN_READY")
        println("KMP_ACCOUNT_SESSION_PRESENT=" + repository.hasSession)
        if (repository.hasSession) {
            runCatching { repository.me() }
                .onSuccess {
                    user = it
                    error = null
                    runCatching { repository.claimPendingOrder() }
                        .onFailure { claimError -> println("KMP_ACCOUNT_CLAIM_FAILED=${claimError.message}") }
                    if (!repository.hasSession) {
                        user = null
                        error = "La sesión ha caducado. Vuelve a iniciar sesión."
                    }
                }
                .onFailure {
                    user = null
                    error = it.message ?: "No se ha podido recuperar la sesión."
                }
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
                    onAddress = { error = null; addressReturnPage = IosAccountPage.HOME; page = IosAccountPage.ADDRESS },
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
                    onForgot = { forgotReturnPage = IosAccountPage.LOGIN; page = IosAccountPage.FORGOT },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.REGISTER -> IosRegisterScreen(
                    repository,
                    onAuthenticated = ::finishAuthentication,
                    onLogin = { page = IosAccountPage.LOGIN },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.FORGOT -> IosForgotPasswordScreen(repository) { page = forgotReturnPage }
                IosAccountPage.PROFILE -> IosProfileMenuScreen(
                    onPersonalData = { page = IosAccountPage.DATA },
                    onAddress = { addressReturnPage = IosAccountPage.PROFILE; page = IosAccountPage.ADDRESS },
                    onPassword = { forgotReturnPage = IosAccountPage.PROFILE; page = IosAccountPage.FORGOT },
                    onDeleteAccount = { onOpenExternalUrl(AccountDeletion.URL) },
                    onLogout = {
                        scope.launch {
                            runCatching { repository.logout() }
                            user = null
                            error = null
                            page = IosAccountPage.HOME
                        }
                    },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.DATA -> IosProfileScreen(repository, user, { user = it }, ::invalidateExpiredSession) { page = IosAccountPage.PROFILE }
                IosAccountPage.ADDRESS -> IosAddressScreen(repository, ::invalidateExpiredSession) { page = addressReturnPage }
                IosAccountPage.ORDERS -> IosOrdersScreen(repository, { selectedOrder = it; page = IosAccountPage.ORDER_DETAIL }, ::invalidateExpiredSession) { page = IosAccountPage.HOME }
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
private fun IosProfileMenuScreen(
    onPersonalData: () -> Unit,
    onAddress: () -> Unit,
    onPassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Mi perfil", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            IosProfileActionRow("Datos personales", "👤", onPersonalData)
            IosProfileActionRow("Dirección de entrega", "⌖", onAddress)
            IosProfileActionRow("Cambiar contraseña", "🔒", onPassword)
            IosPushPreferences()
            IosProfileActionRow(
                AccountDeletion.TITLE,
                "🗑",
                { showDeleteConfirmation = true },
                containerColor = AccountDeletion.background,
                contentColor = AccountDeletion.accent
            )
            IosProfileActionRow(
                "Cerrar sesión",
                "↪",
                onLogout,
                containerColor = Color(0xFFFFECEF),
                contentColor = Color(0xFFC73B4C)
            )
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(AccountDeletion.TITLE) },
            text = { Text(AccountDeletion.MESSAGE) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteAccount()
                }) { Text(AccountDeletion.CONTINUE) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(AccountDeletion.CANCEL) }
            }
        )
    }
}

@Composable
private fun IosProfileActionRow(
    title: String,
    icon: String,
    onClick: () -> Unit,
    containerColor: Color = Color(0xFFF0EDF1),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, modifier = Modifier.width(28.dp), color = contentColor)
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = contentColor)
            Text("›", style = MaterialTheme.typography.titleLarge, color = contentColor)
        }
    }
}

@Composable
private fun IosProfileScreen(
    repository: AccountRepository,
    user: AccountUser?,
    onUserChanged: (AccountUser?) -> Unit,
    onSessionExpired: () -> Unit,
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
                val cleanFirstName = firstName.trim()
                val cleanLastName = lastName.trim()
                if (cleanFirstName.isBlank() || cleanLastName.isBlank()) {
                    error = "Introduce tu nombre y apellidos."
                    busy = false
                    return@Button
                }
                scope.launch {
                    runCatching {
                        val current = repository.customerAddress()
                        repository.saveCustomerAddress(current.copy(firstName = cleanFirstName, lastName = cleanLastName))
                        repository.me()
                    }.onSuccess {
                        onUserChanged(it)
                        notice = "Datos actualizados"
                    }.onFailure {
                        if (!repository.hasSession) {
                            onSessionExpired()
                        } else {
                            error = it.message ?: "No se han podido guardar los datos."
                        }
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Guardando…" else "Guardar cambios") }
    }
}

@Composable
private fun IosAddressScreen(repository: AccountRepository, onSessionExpired: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(AccountCustomerAddress()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { repository.customerAddress() }
            .onSuccess { address = it; error = null; loaded = true }
            .onFailure {
                if (!repository.hasSession) {
                    onSessionExpired()
                } else {
                    error = it.message ?: "No se ha podido cargar la dirección."
                }
                loaded = true
            }
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
                        runCatching {
                            val clean = address.copy(firstName = address.firstName.trim(), lastName = address.lastName.trim(), address1 = address.address1.trim(), address2 = address.address2.trim(), postcode = address.postcode.trim(), city = address.city.trim(), state = address.state.trim(), country = "ES")
                            require(clean.firstName.isNotBlank() && clean.lastName.isNotBlank() && clean.address1.isNotBlank() && clean.postcode.isNotBlank() && clean.city.isNotBlank() && clean.state.isNotBlank()) { "Completa todos los campos obligatorios." }
                            require(clean.postcode.length == 5 && clean.postcode.all { it.isDigit() }) { "Introduce un código postal válido." }
                            require(SPANISH_PROVINCE_CODES.any { it.equals(clean.state, ignoreCase = true) }) { "Selecciona una provincia." }
                            repository.saveCustomerAddress(clean)
                        }
                            .onSuccess { notice = "Dirección actualizada" }
                            .onFailure {
                                if (!repository.hasSession) {
                                    error = "La sesión ha caducado. Vuelve a iniciar sesión."
                                } else {
                                    error = it.message ?: "No se ha podido guardar la dirección."
                                }
                            }
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
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf(emptyList<AccountOrderSummary>()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun retry() { loading = true }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { repository.orders().orders }
            .onSuccess { orders = it; error = null; loading = false }
            .onFailure {
                if (!repository.hasSession) {
                    onSessionExpired()
                    return@onFailure
                }
                error = it.message ?: "No se han podido cargar tus pedidos."
                loading = false
            }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(8.dp))
                Text("Mis pedidos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        when {
            loading -> item { FullScreenLoading("Cargando pedidos") }
            error != null -> item {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { error = null; retry() }) { Text("Reintentar") }
                }
            }
            orders.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Todavía no tienes pedidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> items(orders, key = { it.id }) { order ->
                Card(Modifier.fillMaxWidth().clickable { onOpenOrder(order) }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pedido #${order.number.ifBlank { order.id.toString() }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            IosOrderStatusBadge(order.status, order.statusLabel)
                        }
                        order.dateCreated?.takeIf { it.isNotBlank() }?.let { rawDate ->
                            Spacer(Modifier.height(8.dp))
                            Text("Fecha: ${formatOrderDate(rawDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (order.items.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            order.items.forEach { item ->
                                Text("${item.quantity} × ${item.name}")
                                val variationText = item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }
                                    .joinToString(" · ") { "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${it.value}" }
                                if (variationText.isNotBlank()) Text(variationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        if (order.paymentMethodTitle.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta", style = MaterialTheme.typography.bodySmall)
                        }
                        if (order.total.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Total: ${formatAccountAmount(order.total, order.currency)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (!loading && error == null && orders.isNotEmpty()) {
            item { OutlinedButton(onClick = { retry() }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar pedidos") } }
        }
    }
}

@Composable
private fun IosOrderStatusBadge(status: String, label: String) {
    val visible = label.ifBlank { status }
    val combined = "$status $label".lowercase()
    val background = when {
        "complet" in combined -> Color(0xFFE3F1E8)
        "proces" in combined || "prepar" in combined -> Color(0xFFF4EAD3)
        "enviad" in combined || "shipped" in combined -> Color(0xFFE4EFF8)
        "cancel" in combined || "fallid" in combined || "reembols" in combined -> Color(0xFFF8E3E1)
        else -> Color(0xFFE9E9E7)
    }
    val foreground = when {
        "complet" in combined -> Color(0xFF285C3B)
        "proces" in combined || "prepar" in combined -> Color(0xFF76591F)
        "enviad" in combined || "shipped" in combined -> Color(0xFF245579)
        "cancel" in combined || "fallid" in combined || "reembols" in combined -> Color(0xFF8A332D)
        else -> Color(0xFF5C5C58)
    }
    Surface(shape = RoundedCornerShape(50), color = background, contentColor = foreground) {
        Text(visible, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

private fun formatOrderDate(value: String): String {
    val date = value.take(10)
    val parts = date.split("-")
    return if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else date
}

private fun formatAccountAmount(value: String, currency: String): String {
    val amount = value.replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}
@Composable
private fun IosOrderDetailScreen(order: AccountOrderSummary, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Atrás") }
                Spacer(Modifier.width(8.dp))
                Text("Pedido #${order.number}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            IosOrderStatusBadge(order.status, order.statusLabel)
            order.dateCreated?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text("Fecha: ${formatOrderDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AccountDetailCard("Productos") {
                order.items.forEachIndexed { index, item ->
                    Text("${item.quantity} × ${item.name}", fontWeight = FontWeight.Medium)
                    item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }.forEach {
                        Text("${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${it.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index != order.items.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
        item {
            AccountDetailCard("Pago") {
                Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta")
            }
        }
        val a = order.shippingAddress
        if (a.address1.isNotBlank() || a.city.isNotBlank() || a.postcode.isNotBlank()) {
            item {
                AccountDetailCard("Entrega") {
                    if (a.address1.isNotBlank()) Text(a.address1)
                    if (a.address2.isNotBlank()) Text(a.address2)
                    val location = listOf(a.postcode, a.city, a.state).filter { it.isNotBlank() }.joinToString(" · ")
                    if (location.isNotBlank()) Text(location)
                    if (a.country.isNotBlank()) Text(if (a.country == "ES") "España" else a.country)
                    if (order.shippingMethod.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(order.shippingMethod, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            AccountDetailCard("Resumen") {
                Text("Subtotal: ${formatAccountAmount(order.subtotal, order.currency)}")
                Text(if (order.shippingTotal.isBlank() || order.shippingTotal == "0" || order.shippingTotal == "0.00") "Envío: Gratis" else "Envío: ${formatAccountAmount(order.shippingTotal, order.currency)}")
                Spacer(Modifier.height(8.dp))
                Text("Total: ${formatAccountAmount(order.total, order.currency)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountDetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            content()
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
