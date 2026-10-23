package es.criosrango.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class IosPushDiagnosticSnapshot(
    val permission: String = "NO DETERMINADO",
    val registrationRequested: Boolean = false,
    val tokenAvailable: Boolean = false,
    val didRegisterReceived: Boolean = false,
    val postAttempted: Boolean = false,
    val registrationResult: String = "AÚN SIN DATO",
    val authenticated: String = "AÚN SIN DATO",
    val accepted: String = "AÚN SIN DATO",
    val pendingSync: Boolean = false,
    val newProductsSent: String = "AÚN SIN DATO",
    val orderUpdatesSent: String = "AÚN SIN DATO",
    val installationHash: String = "",
    val errorClass: String = "",
    val errorMessage: String = "",
    val httpStatus: Int? = null,
    val responseBody: String = ""
)

object IosPushDiagnostics {
    var snapshot by mutableStateOf(IosPushDiagnosticSnapshot())
        private set

    fun recordPermission(status: String) {
        snapshot = snapshot.copy(permission = status.safeDiagnosticText(40))
    }

    fun recordRegistrationRequested() {
        snapshot = snapshot.copy(registrationRequested = true)
    }

    fun recordToken(token: String) {
        snapshot = snapshot.copy(
            tokenAvailable = token.isNotBlank(),
            didRegisterReceived = token.isNotBlank(),
            installationHash = token.safeShortHash(),
            errorClass = "",
            errorMessage = ""
        )
    }

    fun recordRegistrationFailure(errorClass: String, message: String) {
        snapshot = snapshot.copy(
            tokenAvailable = false,
            didRegisterReceived = false,
            errorClass = errorClass.safeDiagnosticText(80),
            errorMessage = message.safeDiagnosticText(180)
        )
    }

    fun recordPostAttempt(authenticated: Boolean, newProducts: Boolean, orderUpdates: Boolean) {
        snapshot = snapshot.copy(
            postAttempted = true,
            authenticated = if (authenticated) "SÍ" else "NO",
            newProductsSent = if (newProducts) "SÍ" else "NO",
            orderUpdatesSent = if (orderUpdates) "SÍ" else "NO",
            pendingSync = true
        )
    }

    fun recordPostResult(
        result: String,
        accepted: String,
        pending: Boolean,
        errorClass: String = "",
        errorMessage: String = "",
        httpStatus: Int? = null,
        responseBody: String = ""
    ) {
        snapshot = snapshot.copy(
            registrationResult = result.safeDiagnosticText(80),
            accepted = accepted.safeDiagnosticText(40),
            pendingSync = pending,
            errorClass = errorClass.safeDiagnosticText(80),
            errorMessage = errorMessage.safeDiagnosticText(180),
            httpStatus = httpStatus,
            responseBody = responseBody.safeDiagnosticText(240)
        )
    }
}

private fun String.safeDiagnosticText(maxLength: Int): String =
    replace(Regex("(?i)bearer\\s+\\S+"), "Bearer [redactado]")
        .replace(Regex("(?i)\\b[0-9a-f]{32,}\\b"), "[token redactado]")
        .replace(Regex("[\\r\\n\\t]"), " ")
        .take(maxLength)

private fun String.safeShortHash(): String {
    var hash = 0x811C9DC5.toInt()
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toInt() and 0xFF)
        hash *= 0x01000193
    }
    return hash.toUInt().toString(16).padStart(8, '0')
}
