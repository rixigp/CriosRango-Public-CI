package es.criosrango.shared

import android.text.Html

actual fun platformHtmlToText(html: String): String =
    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
