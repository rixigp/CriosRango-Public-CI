package es.criosrango.shared

expect fun checkoutDiagLog(message: String)

/** Redacts personal/session data before any checkout diagnostic reaches Logcat. */
internal fun sanitizeCheckoutDiag(value: String, limit: Int = 1800): String {
    var safe = value
    safe = safe.replace(
        Regex("""(?i)("?(?:email|phone|first_name|last_name|name|address_1|address_2|address|postcode|postal_code|city|state|country|token|nonce|cookie|authorization|password|order_key|key)"?\s*:\s*)("[^"]*"|[^,}\]\s]+)"""),
        "$1\"[REDACTED]\""
    )
    safe = safe.replace(Regex("(?i)bearer\\s+[a-z0-9._~+/-]+=*"), "Bearer [REDACTED]")
    safe = safe.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[EMAIL]")
    safe = safe.replace(Regex("(?<!\\w)(?:\\+?\\d[\\d .()/-]{7,}\\d)(?!\\w)"), "[PHONE]")
    return safe.take(limit)
}
