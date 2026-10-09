package es.criosrango.shared

import android.util.Log

actual fun checkoutDiagLog(message: String) {
    val debugBuild = runCatching {
        Class.forName("es.criosrango.app.BuildConfig").getField("DEBUG").getBoolean(null)
    }.getOrDefault(false)
    if (debugBuild) Log.d("CHECKOUT_DIAG", message)
}
