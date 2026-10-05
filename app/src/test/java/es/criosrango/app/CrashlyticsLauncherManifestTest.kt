package es.criosrango.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashlyticsLauncherManifestTest {
    @Test
    fun debugCrashActivityIsNeverALauncher() {
        val manifest = File("src/debug/AndroidManifest.xml")
        assertTrue(manifest.isFile, "debug AndroidManifest.xml must exist")
        val xml = manifest.readText()
        assertTrue(xml.contains("android:name=".CrashlyticsDebugCrashActivity""))
        assertTrue(xml.contains("android:exported="false""))
        assertFalse(xml.contains("android.intent.action.MAIN"))
        assertFalse(xml.contains("android.intent.category.LAUNCHER"))
    }

    @Test
    fun mainManifestHasExactlyOneLauncherActivity() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "main AndroidManifest.xml must exist")
        val xml = manifest.readText()
        assertEquals(1, Regex("android.intent.action.MAIN").findAll(xml).count())
        assertEquals(1, Regex("android.intent.category.LAUNCHER").findAll(xml).count())
        assertTrue(xml.contains("android:name=".MainActivity""))
    }
}
