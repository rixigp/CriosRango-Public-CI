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
    val responseBody: String = "",
    val bodyReadOk: String = "AÚN SIN DATO",
    val requestStage: String = "NOT_STARTED",
    val exceptionStage: String = "",
    val lastSentOrderUpdates: String = "AÚN SIN DATO",
    val desiredOrderUpdates: String = "AÚN SIN DATO",
    val confirmedOrderUpdates: String = "AÚN SIN DATO",
    val syncInProgress: Boolean = false,
    val dirty: Boolean = false,
    val desiredRevision: Long = 0,
    val requestRevision: Long = 0,
    val requestGeneration: Long = 0,
    val currentGeneration: Long = 0
)

object IosPushDiagnostics {
    var snapshot by mutableStateOf(IosPushDiagnosticSnapshot())
        private set

    fun recordRequestStage(
        stage: String,
        orderUpdates: Boolean? = null,
        newProducts: Boolean? = null,
        revision: Long? = null,
        generation: Long? = null,
        exceptionStage: String? = null
    ) {
        snapshot = snapshot.copy(
            requestStage = stage.safeDiagnosticText(48),
            lastSentOrderUpdates = orderUpdates?.let { if (it) "SÍ" else "NO" } ?: snapshot.lastSentOrderUpdates,
            newProductsSent = newProducts?.let { if (it) "SÍ" else "NO" } ?: snapshot.newProductsSent,
            orderUpdatesSent = orderUpdates?.let { if (it) "SÍ" else "NO" } ?: snapshot.orderUpdatesSent,
            requestRevision = revision ?: snapshot.requestRevision,
            requestGeneration = generation ?: snapshot.requestGeneration,
            exceptionStage = exceptionStage?.safeDiagnosticText(48)
                ?: if (stage.endsWith("ENTERED") || stage == "REQUEST_CREATED") "" else snapshot.exceptionStage
        )
    }

    fun recordCoordinatorState(
        desiredOrderUpdates: Boolean,
        confirmedOrderUpdates: Boolean,
        syncInProgress: Boolean,
        dirty: Boolean,
        revision: Long,
        generation: Long
    ) {
        snapshot = snapshot.copy(
            desiredOrderUpdates = if (desiredOrderUpdates) "SÍ" else "NO",
            confirmedOrderUpdates = if (confirmedOrderUpdates) "SÍ" else "NO",
            syncInProgress = syncInProgress,
            dirty = dirty,
            desiredRevision = revision,
            currentGeneration = generation
        )
    }

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
            pendingSync = true,
            httpStatus = null,
            responseBody = "",
            bodyReadOk = "AÚN SIN DATO",
            exceptionStage = ""
        )
    }

    // HTTP response bodies can contain PII or credentials; keep the parameter for callers but never persist it.
    @Suppress("UNUSED_PARAMETER")
    fun recordPostResult(
        result: String,
        accepted: String,
        pending: Boolean,
        errorClass: String = "",
        errorMessage: String = "",
        httpStatus: Int? = null,
        responseBody: String = "",
        bodyReadOk: Boolean? = null
    ) {
        snapshot = snapshot.copy(
            registrationResult = result.safeDiagnosticText(80),
            accepted = accepted.safeDiagnosticText(40),
            pendingSync = pending,
            errorClass = errorClass.safeDiagnosticText(80),
            errorMessage = errorMessage.safeDiagnosticText(180),
            httpStatus = httpStatus,
            responseBody = "",
            bodyReadOk = bodyReadOk?.let { if (it) "SÍ" else "NO" } ?: snapshot.bodyReadOk,
            requestStage = if (httpStatus != null) "RESPONSE_RECEIVED" else snapshot.requestStage
        )
    }
}

internal fun String.safeDiagnosticText(maxLength: Int): String =
    replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "Bearer [redactado]")
        .replace(Regex("""(?i)\b(cookie|set-cookie)\b\s*[:=]\s*[^\r\n]+""")) { match -> "${match.groupValues[1]}=[redactado]" }
        .replace(Regex("""(?i)(["']?(?:authorization|account[_-]?token|cart-token|nonce|order[_-]?key|payment[_-]?url|paymenturl|fid|installation[_-]?id|token|api[_-]?key|secret|password|email|e-mail|phone|telephone|address(?:[_-]?[12])?|first[_-]?name|last[_-]?name|postcode|zip(?:code)?|billing|shipping|card(?:[_-]?(?:number|token))?|cvv|cvc|bizum|request[_-]?body|response[_-]?body|payload)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^,\s&;]+)""")) { match ->
            "${match.groupValues[1]}[redactado]"
        }

        .replace(Regex("""(?i)https?://[^\s"'<>]+"""), "[URL redactada]")
        .replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[email redactado]")
        .replace(Regex("(?<!\\w)(?:\\+?\\d[\\d .()\\-]{7,}\\d)(?!\\w)"), "[teléfono redactado]")
        .replace(Regex("(?i)\\b[0-9a-f]{32,}\\b"), "[token redactado]")
        .replace(Regex("eyJ[^\\s]+"), "[JWT redactado]")
        .replace(Regex(if (maxLength > 240) "[\\r\\t]" else "[\\r\\n\\t]"), " ")
        .take(maxLength)

private fun String.safeShortHash(): String {
    var hash = 0x811C9DC5.toInt()
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toInt() and 0xFF)
        hash *= 0x01000193
    }
    return hash.toUInt().toString(16).padStart(8, '0')
}
