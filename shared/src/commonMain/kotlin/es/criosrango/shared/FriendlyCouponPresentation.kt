package es.criosrango.shared

/**
 * Shared user-facing coupon naming for Android and iOS.
 * walletAmount must come from the wallet reward or applied-discount model.
 */
fun friendlyAppliedCouponName(label: String, code: String, walletAmount: String? = null): String {
    val key = "$label $code".lowercase()
    // WooCommerce may expose the wallet prefix in either the coupon code or its label.
    // Check both fields before reaching the generic fallback.
    val walletCoupon = sequenceOf(code, label).any {
        it.contains("cr-monedero-", ignoreCase = true)
    }
    return when {
        walletCoupon -> walletAmount?.takeIf { it.isNotBlank() }?.let { "Saldo de monedero · $it" } ?: "Saldo de monedero"
        key.contains("blackcrios") || key.contains("black friday") -> "Black Friday 20%"
        key.contains("bienvenida") || key.contains("welcome") -> "Bienvenida 10%"
        key.contains("cr-cumple-") || key.contains("cumple") || key.contains("birthday") -> "Cumpleaños 15%"
        label.isNotBlank() && !label.equals(code, ignoreCase = true) &&
            !label.startsWith("cr-", ignoreCase = true) -> label
        else -> "Descuento aplicado"
    }
}
