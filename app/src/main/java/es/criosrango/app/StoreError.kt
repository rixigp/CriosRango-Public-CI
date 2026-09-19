package es.criosrango.app

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class StoreErrorType { NO_INTERNET, SERVER_UNAVAILABLE, TIMEOUT, SESSION_EXPIRED, UNEXPECTED }

data class StoreUiError(val type: StoreErrorType) {
    val title: String get() = when (type) {
        StoreErrorType.NO_INTERNET -> "Sin conexión a Internet"
        StoreErrorType.SERVER_UNAVAILABLE -> "No podemos conectar con la tienda"
        StoreErrorType.TIMEOUT -> "La conexión está tardando demasiado"
        StoreErrorType.SESSION_EXPIRED -> "Tu sesión ha caducado"
        StoreErrorType.UNEXPECTED -> "Ha ocurrido un problema"
    }
    val message: String get() = when (type) {
        StoreErrorType.NO_INTERNET -> "Comprueba tu conexión y vuelve a intentarlo."
        StoreErrorType.SERVER_UNAVAILABLE -> "El servidor no está disponible en este momento. Inténtalo de nuevo en unos instantes."
        StoreErrorType.TIMEOUT -> "Comprueba tu conexión o vuelve a intentarlo."
        StoreErrorType.SESSION_EXPIRED -> "Vuelve a iniciar sesión para continuar."
        StoreErrorType.UNEXPECTED -> "Inténtalo de nuevo."
    }
    val icon: ImageVector get() = when (type) {
        StoreErrorType.NO_INTERNET -> Icons.Outlined.WifiOff
        StoreErrorType.SERVER_UNAVAILABLE -> Icons.Outlined.CloudOff
        StoreErrorType.TIMEOUT -> Icons.Outlined.Schedule
        StoreErrorType.SESSION_EXPIRED -> Icons.Outlined.Lock
        StoreErrorType.UNEXPECTED -> Icons.Outlined.ErrorOutline
    }
}

fun Exception.toStoreUiError(authenticated: Boolean = false): StoreUiError = when (this) {
    is SocketTimeoutException, is TimeoutCancellationException -> StoreUiError(StoreErrorType.TIMEOUT)
    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketException, is IOException -> StoreUiError(StoreErrorType.NO_INTERNET)
    is HttpException -> when {
        code() in 500..599 -> StoreUiError(StoreErrorType.SERVER_UNAVAILABLE)
        authenticated && code() in 401..403 -> StoreUiError(StoreErrorType.SESSION_EXPIRED)
        else -> StoreUiError(StoreErrorType.UNEXPECTED)
    }
    else -> StoreUiError(StoreErrorType.UNEXPECTED)
}

fun Exception.toStoreUiErrorForCatalog(): StoreUiError = toStoreUiError(authenticated = false)

@Composable
fun StoreErrorState(error: StoreUiError, padding: PaddingValues, onRetry: (() -> Unit)? = null, onLogin: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(error.icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Text(error.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(error.message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(18.dp))
        if (error.type == StoreErrorType.SESSION_EXPIRED && onLogin != null) OutlinedButton(onClick = onLogin) { Text("Iniciar sesión") }
        else if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}
