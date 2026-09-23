package es.criosrango.shared

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSAttributedString
import platform.Foundation.NSCharacterEncodingDocumentAttribute
import platform.Foundation.NSDocumentTypeDocumentAttribute
import platform.Foundation.NSHTMLTextDocumentType
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding

@OptIn(ExperimentalForeignApi::class)
actual fun platformHtmlToText(html: String): String {
    val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return ""
    val attributed = NSAttributedString.create(
        data = data,
        options = mapOf(
            NSDocumentTypeDocumentAttribute to NSHTMLTextDocumentType,
            NSCharacterEncodingDocumentAttribute to NSUTF8StringEncoding
        ),
        documentAttributes = null,
        error = null
    ) ?: return ""
    return attributed.string
}
