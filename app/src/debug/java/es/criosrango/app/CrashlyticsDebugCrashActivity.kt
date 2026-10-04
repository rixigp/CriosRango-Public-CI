package es.criosrango.app

import android.app.Activity
import android.os.Bundle

/** Debug-only hook for one controlled physical Crashlytics crash validation. */
class CrashlyticsDebugCrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        throw IllegalStateException("Crashlytics debug test crash")
    }
}
