package es.criosrango.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun backupRulesExcludeAccountSessionV2() {
        assertTrue(hasSharedPrefExclusion(context.resources.getXml(R.xml.backup_rules), "criosrango_account_session_v2.xml"))
    }

    @Test
    fun dataExtractionRulesExcludeAccountSessionV2FromCloudBackup() {
        assertTrue(hasSharedPrefExclusionInSection(context.resources.getXml(R.xml.data_extraction_rules), "cloud-backup", "criosrango_account_session_v2.xml"))
    }

    @Test
    fun dataExtractionRulesExcludeAccountSessionV2FromDeviceTransfer() {
        assertTrue(hasSharedPrefExclusionInSection(context.resources.getXml(R.xml.data_extraction_rules), "device-transfer", "criosrango_account_session_v2.xml"))
    }

    private fun hasSharedPrefExclusion(parser: XmlPullParser, path: String): Boolean =
        findExclusion(parser, null, path)

    private fun hasSharedPrefExclusionInSection(parser: XmlPullParser, section: String, path: String): Boolean =
        findExclusion(parser, section, path)

    private fun findExclusion(parser: XmlPullParser, section: String?, path: String): Boolean {
        var currentSection: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "cloud-backup" || name == "device-transfer") currentSection = name
                if (name == "exclude" &&
                    parser.getAttributeValue(null, "domain") == "sharedpref" &&
                    parser.getAttributeValue(null, "path") == path &&
                    (section == null || currentSection == section)
                ) return true
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name == "cloud-backup" || parser.name == "device-transfer") currentSection = null
            }
            event = parser.next()
        }
        return false
    }
}
