package es.criosrango.shared

import android.util.Log

internal actual fun crCheckoutPointsDebug(message: String) {
    if (BuildConfig.DEBUG) Log.d("CRCheckoutPoints", message)
}
