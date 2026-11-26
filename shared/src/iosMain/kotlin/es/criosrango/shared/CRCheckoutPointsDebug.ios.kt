package es.criosrango.shared

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
internal actual fun crCheckoutPointsDebug(message: String) {
    if (Platform.isDebugBinary) println("CRCheckoutPoints $message")
}
