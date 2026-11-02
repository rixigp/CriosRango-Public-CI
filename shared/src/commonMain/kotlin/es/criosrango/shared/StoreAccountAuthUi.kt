package es.criosrango.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
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
        if (!repository.hasSession) return
        mutableSnapshot.value = StoreAccountAuthSnapshot(
            status = StoreAccountAuthStatus.AUTHENTICATED,
            user = user,
            loading = false
        )
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
    onBack: () -> Unit,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit,
    onInfoPage: (StoreAccountInfoPage) -> Unit,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    contentTopSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    headerHorizontalPadding: androidx.compose.ui.unit.Dp = 20.dp
) {
    val hasUserName = !fullName.isNullOrBlank()
    val nameParts = fullName.orEmpty().trim().split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .filterNot { it.lowercase() in setOf("de", "del", "la", "las", "los", "y") }
    val initials = buildString {
        nameParts.firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }
        nameParts.getOrNull(1)?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifBlank { "—" }

    Column(modifier.fillMaxWidth()) {
        StorePageHeader(title = "Cuenta", onBack = onBack, horizontalPadding = headerHorizontalPadding)
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(contentTopSpacing))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF1F2F1),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(Modifier.size(56.dp), shape = CircleShape, color = Color(0xFFE1E5E2)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                initials,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF183B35)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when { hasUserName -> fullName.orEmpty(); !email.isNullOrBlank() -> "Cuenta"; else -> "Inicia sesión o regístrate" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF183B35)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            email?.takeIf(String::isNotBlank) ?: if (hasUserName) "" else "Accede a tu cuenta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF59645D)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountRootAccess(
                    "Pedidos", "Consulta y gestiona tus pedidos", Icons.Outlined.Inventory,
                    Color(0xFFEAF4EC), Color(0xFF287448), Modifier.weight(1f), onOrders
                )
                AccountRootAccess(
                    "Cupones y promociones", "Descuentos y ofertas exclusivas", Icons.Outlined.Sell,
                    Color(0xFFF1ECFA), Color(0xFF7652A8), Modifier.weight(1f), onWallet
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountRootAccess(
                    "Perfil", "Tus datos, direcciones y preferencias", Icons.Outlined.Person,
                    Color(0xFFFAF4E5), Color(0xFF9A741F), Modifier.weight(1f), onProfile
                )
                AccountRootAccess(
                    "Ayuda", "Preguntas frecuentes y contacto", Icons.AutoMirrored.Outlined.HelpOutline,
                    Color(0xFFEAF2FA), Color(0xFF326D9C), Modifier.weight(1f), onHelp
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Información",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF183B35)
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    StoreAccountInformationRow("Cambios y devoluciones", Icons.Outlined.Restore, false) {
                        onInfoPage(StoreAccountInfoPage.RETURNS)
                    }
                    StoreAccountInformationRow("Condiciones de contratación", Icons.Outlined.Description, false) {
                        onInfoPage(StoreAccountInfoPage.TERMS)
                    }
                    StoreAccountInformationRow("Política de privacidad", Icons.Outlined.PrivacyTip, false) {
                        onInfoPage(StoreAccountInfoPage.PRIVACY)
                    }
                    StoreAccountInformationRow("Aviso legal", Icons.Outlined.Description, false) {
                        onInfoPage(StoreAccountInfoPage.LEGAL)
                    }
                    StoreAccountInformationRow("Política de cookies", Icons.Outlined.Cookie, true) {
                        onInfoPage(StoreAccountInfoPage.COOKIES)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (appVersion.isNotBlank()) {
                Text(
                    "Versión $appVersion",
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccountRootAccess(
    title: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 146.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(27.dp))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF183B35)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4E5A53)
            )
        }
    }
}

@Composable
private fun StoreAccountInformationRow(
    title: String,
    icon: ImageVector,
    last: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1F6B42), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF202923)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF59645D),
                modifier = Modifier.size(22.dp)
            )
        }
        if (!last) HorizontalDivider(
            Modifier.padding(start = 52.dp),
            color = Color(0xFFE8EBE8),
            thickness = 1.dp
        )
    }
}

/** Approved Críos&Rango identity colors shared by Android and iOS Account UI. */
object StoreAccountColors {
    val primary = Color(0xFF183B35)
    val switchCheckedTrack = Color(0xFFB9D8CE)
    val switchCheckedThumb = Color(0xFF183B35)
}

@Composable
private fun Modifier.accountKeyboardDismiss(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return pointerInput(focusManager, keyboard) {
        detectTapGestures(onTap = { focusManager.clearFocus(); keyboard?.hide() })
    }
}

@Composable
fun StoreProfileMenuScreen(
    onPersonalData: () -> Unit,
    onAddress: () -> Unit,
    onPassword: () -> Unit,
    notificationPreferences: @Composable () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        StorePageHeader(title = "Perfil", onBack = onBack)
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE6EBE8)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    StoreProfileGroupedRow("Datos personales", androidx.compose.material.icons.Icons.Outlined.Person, onPersonalData)
                    HorizontalDivider(color = Color(0xFFE8ECE9), thickness = 1.dp)
                    StoreProfileGroupedRow("Dirección de entrega", androidx.compose.material.icons.Icons.Outlined.LocationOn, onAddress)
                    HorizontalDivider(color = Color(0xFFE8ECE9), thickness = 1.dp)
                    StoreProfileGroupedRow("Cambiar contraseña", androidx.compose.material.icons.Icons.Outlined.Lock, onPassword)
                }
            }
            notificationPreferences()
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = { showDeleteConfirmation = true }),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AccountDeletion.background),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = null, tint = AccountDeletion.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(AccountDeletion.TITLE, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = AccountDeletion.accent)
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoggingOut, onClick = onLogout),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECEF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFC73B4C))
                    } else {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = Color(0xFFC73B4C), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (isLoggingOut) "Cerrando sesión…" else "Cerrar sesión", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFC73B4C))
                }
            }
        }
    }
    if (showDeleteConfirmation) AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text(AccountDeletion.TITLE) },
        text = { Text(AccountDeletion.MESSAGE) },
        confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; onDeleteAccount() }) { Text(AccountDeletion.CONTINUE) } },
        dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text(AccountDeletion.CANCEL) } }
    )
}

@Composable
private fun StoreProfileGroupedRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF24332E))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF78847E), modifier = Modifier.size(21.dp))
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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val passwordFocus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().accountKeyboardDismiss()) {
        StorePageHeader(title = "Iniciar sesión", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = login, onValueChange = onLoginChanged, label = { Text("Correo electrónico") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = StoreAccountColors.primary, focusedLabelColor = StoreAccountColors.primary, cursorColor = StoreAccountColors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() })
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = onPasswordChanged, label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = StoreAccountColors.primary, focusedLabelColor = StoreAccountColors.primary, cursorColor = StoreAccountColors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() })
            )
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { if (login.isBlank() || password.isBlank()) onSubmit(login, password) else onSubmit(login.trim(), password) }, enabled = !loading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = StoreAccountColors.primary, contentColor = Color.White)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("Iniciar sesión")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = StoreAccountColors.primary)) { Text("He olvidado mi contraseña") }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = StoreAccountColors.primary)) { Text("Crear cuenta") }
        }
    }
}
