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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

enum class StoreAccountAuthStatus { CHECKING, AUTHENTICATED, UNAUTHENTICATED }

data class StoreAccountAuthSnapshot(
    val status: StoreAccountAuthStatus = StoreAccountAuthStatus.CHECKING,
    val user: AccountUser? = null,
    val loading: Boolean = true,
    val error: String? = null
)

fun normalizeStoreLoginError(statusCode: Int?): String = when (statusCode) {
    401 -> "Correo electrónico o contraseña incorrectos."
    429 -> "Demasiados intentos. Espera unos minutos."
    else -> "No hemos podido iniciar sesión."
}

/** Shared observable auth/session state. Platform token stores remain injected through AccountRepository. */
class StoreAccountSessionState(private val repository: AccountRepository) {
    private val mutableSnapshot = MutableStateFlow(StoreAccountAuthSnapshot())
    val snapshot: StateFlow<StoreAccountAuthSnapshot> = mutableSnapshot.asStateFlow()
    private var actionInFlight = false

    suspend fun restore() {
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
                    error = "No hemos podido recuperar la sesión."
                )
            }
        }
    }

    suspend fun login(login: String, password: String): AccountUser? {
        if (login.isBlank() || password.isBlank()) {
            mutableSnapshot.value = mutableSnapshot.value.copy(error = "Introduce tu correo y contraseña.")
            return null
        }
        synchronized(this) {
            if (actionInFlight) return null
            actionInFlight = true
        }
        mutableSnapshot.value = mutableSnapshot.value.copy(loading = true, error = null)
        try {
            val user = repository.login(login.trim(), password)
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = StoreAccountAuthStatus.AUTHENTICATED, user = user, loading = false
            )
            return user
        } catch (failure: Exception) {
            val status = (failure as? ResponseException)?.response?.status?.value
            mutableSnapshot.value = StoreAccountAuthSnapshot(
                status = if (repository.hasSession) StoreAccountAuthStatus.CHECKING else StoreAccountAuthStatus.UNAUTHENTICATED,
                loading = false,
                error = if (failure is IllegalStateException && failure.message == "No se ha podido guardar la sesión de cuenta.")
                    failure.message else normalizeStoreLoginError(status)
            )
            return null
        } finally {
            synchronized(this) { actionInFlight = false }
        }
    }

    fun clearError() {
        mutableSnapshot.value = mutableSnapshot.value.copy(error = null)
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
    onInfoPage: (AccountInfoPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val authenticated = !fullName.isNullOrBlank() && !email.isNullOrBlank()
    val parts = fullName.orEmpty().trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val initials = buildString {
        parts.firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }
        parts.drop(1).firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifBlank { "CR" }
    Column(modifier.fillMaxWidth()) {
        Text("Cuenta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        Text("♙", style = MaterialTheme.typography.headlineMedium)
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
            AccountRootAccess("Pedidos", Modifier.weight(1f), onOrders)
            AccountRootAccess("Cupones y promociones", Modifier.weight(1f), onWallet)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountRootAccess("Perfil", Modifier.weight(1f), onProfile)
            AccountRootAccess("Ayuda", Modifier.weight(1f), onHelp)
        }
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Información", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Cambios y devoluciones", Modifier.fillMaxWidth().clickable { onInfoPage(AccountInfoPage.RETURNS) }.padding(vertical = 6.dp))
            Text("Condiciones de contratación", Modifier.fillMaxWidth().clickable { onInfoPage(AccountInfoPage.TERMS) }.padding(vertical = 6.dp))
            Text("Política de privacidad", Modifier.fillMaxWidth().clickable { onInfoPage(AccountInfoPage.PRIVACY) }.padding(vertical = 6.dp))
            Text("Aviso legal", Modifier.fillMaxWidth().clickable { onInfoPage(AccountInfoPage.LEGAL) }.padding(vertical = 6.dp))
            Text("Política de cookies", Modifier.fillMaxWidth().clickable { onInfoPage(AccountInfoPage.COOKIES) }.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Versión $appVersion", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountRootAccess(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
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
            TextButton(onClick = onBack) { Text("‹ Atrás") }
            Spacer(Modifier.width(8.dp))
            Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
