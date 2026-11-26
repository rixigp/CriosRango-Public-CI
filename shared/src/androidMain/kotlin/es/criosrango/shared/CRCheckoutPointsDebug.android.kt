package es.criosrango.shared

import android.util.Log

internal actual fun crCheckoutPointsDebug(message: String) {
    if (BuildConfig.DEBUG) {
        try {
            Log.d("CRCheckoutPoints", message)
        } catch (_: RuntimeException) {
            println("CRCheckoutPoints: $message")
        }
    }
}
