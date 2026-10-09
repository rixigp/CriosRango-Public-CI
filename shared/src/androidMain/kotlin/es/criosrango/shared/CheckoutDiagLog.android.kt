package es.criosrango.shared

import android.util.Log

actual fun checkoutDiagLog(message: String) {
    val debugBuild = runCatching {
        Class.forName("es.criosrango.app.BuildConfig").getField("DEBUG").getBoolean(null)
    }.getOrDefault(false)
    if (debugBuild) {
        if (message.startsWith("LOGOUT_CART_DIAG ")) Log.d("LOGOUT_CART_DIAG", message.removePrefix("LOGOUT_CART_DIAG "))
        else Log.d("CHECKOUT_DIAG", message)
    }
}
