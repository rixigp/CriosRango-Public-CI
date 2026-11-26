package es.criosrango.shared

import kotlin.native.Platform

internal actual fun crCheckoutPointsDebug(message: String) {
    if (Platform.isDebugBinary) println("CRCheckoutPoints $message")
}
