package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountUser
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

enum class StoreAccountAuthStatus { CHECKING, AUTHENTICATED, UNAUTHENTICATED }
enum class StoreAccountInfoPage { RETURNS, TERMS, PRIVACY, LEGAL, COOKIES }


class StoreAccountActionGate {
    private val mutex = Mutex()
    fun tryAcquire(): Boolean = mutex.tryLock()
    fun release() = mutex.unlock()
}

data class StoreAccountAuthSnapshot(
    val status: StoreAccountAuthStatus = StoreAccountAuthStatus.CHECKING,
    val user: AccountUser? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val cause: Throwable? = null
)

fun normalizeStoreLoginCredentials(login: String, password: String): Pair<String, String> = login.trim() to password

fun normalizeStoreLoginError(statusCode: Int?): String = when (statusCode) {
    401 -> "Correo electrónico o contraseña incorrectos."
    429 -> "Demasiados intentos. Espera unos minutos."
    else -> "No hemos podido iniciar sesión."
}

/** Shared observable auth/session state. Platform token stores remain injected through AccountRepository. */
class StoreAccountSessionState(private val repository: AccountRepository) {
    private val mutableSnapshot = MutableStateFlow(StoreAccountAuthSnapshot())
    private var restoreStarted = false
    val snapshot: StateFlow<StoreAccountAuthSnapshot> = mutableSnapshot.asStateFlow()
    private val actionGate = StoreAccountActionGate()

    suspend fun restore() {
        if (restoreStarted) return
        restoreStarted = true
        if (!repository.hasSession) {
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = StoreAccountAuthStatus.UNAUTHENTICATED, loading = false
            )
            return
        }
        mutableSnapshot.value = StoreAccountAuthSnapshot(status = StoreAccountAuthStatus.CHECKING, loading = true)
        try {
            val user = repository.me()
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = StoreAccountAuthStatus.AUTHENTICATED, user = user, loading = false
            )
        } catch (failure: Exception) {
            if (!repository.hasSession) {
                mutableSnapshot.value = StoreAccountAuthSnapshot(
                    status = StoreAccountAuthStatus.UNAUTHENTICATED, loading = false
                )
            } else {
                mutableSnapshot.value = StoreAccountAuthSnapshot(
                    status = StoreAccountAuthStatus.CHECKING,
                    loading = false,
                    error = "No hemos podido recuperar la sesión.",
                    cause = failure
                )
            }
        }
    }

    suspend fun login(login: String, password: String): AccountUser? {
        if (login.isBlank() || password.isBlank()) {
            mutableSnapshot.value = mutableSnapshot.value.copy(error = "Introduce tu correo y contraseña.")
            return null
        }
        if (!actionGate.tryAcquire()) return null
        mutableSnapshot.value = mutableSnapshot.value.copy(loading = true, error = null)
        try {
            val credentials = normalizeStoreLoginCredentials(login, password)
            val user = repository.login(credentials.first, credentials.second)
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = StoreAccountAuthStatus.AUTHENTICATED, user = user, loading = false
            )
            return user
        } catch (failure: Exception) {
            val status = (failure as? ResponseException)?.response?.status?.value
            val message = when {
                failure is IllegalStateException && failure.message == "No se ha podido guardar la sesión de cuenta." -> failure.message
                status != null -> normalizeStoreLoginError(status)
                else -> "No hemos podido conectar con la tienda."
            }
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = if (repository.hasSession) StoreAccountAuthStatus.CHECKING else StoreAccountAuthStatus.UNAUTHENTICATED,
                loading = false,
                error = message,
                cause = failure
            )
            return null
        } finally {
            actionGate.release()
        }
    }

    fun clearError() {
        mutableSnapshot.value = mutableSnapshot.value.copy(error = null)
    }

    /** Publish profile edits immediately to every consumer of the authenticated account. */
    fun publishAuthenticatedUser(user: AccountUser) {
        val current = mutableSnapshot.value
        if (current.status == StoreAccountAuthStatus.AUTHENTICATED) {
            mutableSnapshot.value = current.copy(user = user, loading = false, error = null, cause = null)
        }
    }

    fun logout() {
        repository.clearLocalSession()
        mutableSnapshot.value = StoreAccountAuthSnapshot(
            status = StoreAccountAuthStatus.UNAUTHENTICATED, loading = false
        )
    }
}

@Composable
fun StoreAccountRootScreen(
    fullName: String?,
    email: String?,
    appVersion: String,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit,
    onInfoPage: (StoreAccountInfoPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val authenticated = !fullName.isNullOrBlank() && !email.isNullOrBlank()
    val parts = fullName.orEmpty().trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val initials = buildString {
        parts.firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }
        parts.drop(1).firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifBlank { "CR" }
    Column(modifier.fillMaxWidth()) {
        Text("Cuenta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF183B35))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        if (authenticated) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(60.dp), shape = CircleShape, color = androidx.compose.ui.graphics.Color(0xFFE9E6EA)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(fullName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onLogin).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(Modifier.size(60.dp), shape = CircleShape, color = androidx.compose.ui.graphics.Color(0xFFE3EFEB)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF183B35), modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Inicia sesión o regístrate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text("Accede a tu cuenta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(if (authenticated) 24.dp else 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountRootAccess("Pedidos", Icons.Outlined.History, Modifier.weight(1f), onOrders)
            AccountRootAccess("Cupones y promociones", Icons.Outlined.Sell, Modifier.weight(1f), onWallet)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountRootAccess("Perfil", Icons.Outlined.Person, Modifier.weight(1f), onProfile)
            AccountRootAccess("Ayuda", Icons.AutoMirrored.Outlined.HelpOutline, Modifier.weight(1f), onHelp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Información", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        StoreAccountInformationRow("Cambios y devoluciones", Icons.Outlined.Restore) { onInfoPage(StoreAccountInfoPage.RETURNS) }
        StoreAccountInformationRow("Condiciones de contratación", Icons.Outlined.Description) { onInfoPage(StoreAccountInfoPage.TERMS) }
        StoreAccountInformationRow("Política de privacidad", Icons.Outlined.PrivacyTip) { onInfoPage(StoreAccountInfoPage.PRIVACY) }
        StoreAccountInformationRow("Aviso legal", Icons.Outlined.Description) { onInfoPage(StoreAccountInfoPage.LEGAL) }
        StoreAccountInformationRow("Política de cookies", Icons.Outlined.Cookie) { onInfoPage(StoreAccountInfoPage.COOKIES) }
        Spacer(Modifier.height(20.dp))
        if (appVersion.isNotBlank()) Text("Versión $appVersion", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccountRootAccess(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(androidx.compose.ui.graphics.Color(0xFFE3EFEB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF183B35), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

@Composable
private fun StoreAccountInformationRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null)
            }
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun StoreLoginScreen(
    login: String,
    password: String,
    loading: Boolean,
    error: String?,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
            Spacer(Modifier.width(8.dp))
            Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF183B35))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = login, onValueChange = onLoginChanged, label = { Text("Correo electrónico") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = onPasswordChanged, label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (login.isBlank() || password.isBlank()) onSubmit(login, password) else onSubmit(login.trim(), password) },
                enabled = !loading, modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Iniciar sesión")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) { Text("He olvidado mi contraseña") }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
        }
    }
}
