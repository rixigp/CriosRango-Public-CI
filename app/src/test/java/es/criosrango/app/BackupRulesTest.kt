package es.criosrango.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {
    @Test
    fun backupRulesExcludeAccountSessionV2() {
        assertTrue(hasSharedPrefExclusion("src/main/res/xml/backup_rules.xml", null))
    }

    @Test
    fun dataExtractionRulesExcludeAccountSessionV2FromCloudBackup() {
        assertTrue(hasSharedPrefExclusion("src/main/res/xml/data_extraction_rules.xml", "cloud-backup"))
    }

    @Test
    fun dataExtractionRulesExcludeAccountSessionV2FromDeviceTransfer() {
        assertTrue(hasSharedPrefExclusion("src/main/res/xml/data_extraction_rules.xml", "device-transfer"))
    }

    private fun hasSharedPrefExclusion(filePath: String, requiredSection: String?): Boolean {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(filePath))
        val exclusions = document.getElementsByTagName("exclude")
        for (index in 0 until exclusions.length) {
            val element = exclusions.item(index) as Element
            if (element.getAttribute("domain") != "sharedpref" ||
                element.getAttribute("path") != "criosrango_account_session_v2.xml"
            ) continue

            if (requiredSection == null || element.parentNode.nodeName == requiredSection) {
                return true
            }
        }
        return false
    }
}
