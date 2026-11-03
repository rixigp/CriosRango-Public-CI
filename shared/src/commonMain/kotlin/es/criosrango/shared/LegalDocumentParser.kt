package es.criosrango.shared

data class LegalInlineLink(val start: Int, val end: Int, val url: String)
data class LegalTextBlock(val text: String, val links: List<LegalInlineLink> = emptyList(), val strongRanges: List<IntRange> = emptyList())
sealed interface LegalBlock {
    data class Paragraph(val inline: LegalTextBlock) : LegalBlock
    data class OrderedList(val items: List<LegalTextBlock>) : LegalBlock
    data class UnorderedList(val items: List<LegalTextBlock>) : LegalBlock
    data class KeyValueTable(val rows: List<Pair<LegalTextBlock, LegalTextBlock>>) : LegalBlock
}
data class LegalSection(val title: String, val blocks: List<LegalBlock>)
data class LegalDocument(val title: String, val sections: List<LegalSection>, val continuousBlocks: List<LegalBlock>, val rawHtml: String)

private val legalEntityMap = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
    "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "mdash" to "—", "ndash" to "–",
    "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•",
    "middot" to "·", "laquo" to "«", "raquo" to "»", "cent" to "¢", "pound" to "£", "yen" to "¥",
    "times" to "×", "divide" to "÷", "para" to "¶", "sect" to "§", "deg" to "°", "acute" to "´",
    "uuml" to "ü", "Uuml" to "Ü", "ntilde" to "ñ", "Ntilde" to "Ñ", "aacute" to "á", "eacute" to "é",
    "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í",
    "Oacute" to "Ó", "Uacute" to "Ú", "agrave" to "à", "egrave" to "è", "ograve" to "ò", "ccedil" to "ç"
)
private fun legalDecodeEntities(source: String): String = source.replace(Regex("""&(#(?:[0-9]+|x[0-9a-fA-F]+)|[A-Za-z][A-Za-z0-9]+);""")) { m ->
    val entity = m.groupValues[1]
    if (entity.startsWith("#")) {
        val code = runCatching { if (entity.startsWith("#x", true)) entity.drop(2).toLong(16) else entity.drop(1).toLong() }.getOrNull()
        code?.let { legalCodePoint(it) } ?: m.value
    } else legalEntityMap[entity] ?: m.value
}
private fun legalCodePoint(code: Long): String? {
    if (code !in 0..0x10FFFF || code in 0xD800..0xDFFF) return null
    if (code <= 0xFFFF) return code.toInt().toChar().toString()
    val adjusted = (code - 0x10000).toInt()
    return ((0xD800 + (adjusted shr 10)).toChar().toString() + (0xDC00 + (adjusted and 0x3FF)).toChar())
}
private fun legalNormalizeShortcodes(html: String): String =
    html.replace(Regex("""\[(?:/)?(?:vc|nectar|salient)_[^\]]*\]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[(?:/)?(?:vc_column_text|vc_row|vc_column|nectar_ct|salient_text)\s*[^\]]*\]""", RegexOption.IGNORE_CASE), "")

object LegalDocumentParser {
    fun parse(titleHtml: String, contentHtml: String): LegalDocument {
        val title = legalDecodeEntities(titleHtml.replace(Regex("<[^>]+>"), "")).trim()
        val html = legalNormalizeShortcodes(contentHtml)
        val headingFound = Regex("""<h[1-4]\b[^>]*>.*?</h[1-4]>""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL).containsMatchIn(html)
        val blocks = parseBlocks(html)
        val sections = if (headingFound) {
            val result = mutableListOf<LegalSection>()
            var currentTitle = ""
            var currentBlocks = mutableListOf<LegalBlock>()
            // Reparse sequentially so only semantic h1-h4 establish sections.
            val tokens = Regex("""(<h[1-4]\b[^>]*>.*?</h[1-4]>|<p\b[^>]*>.*?</p>|<ol\b[^>]*>.*?</ol>|<ul\b[^>]*>.*?</ul>|<table\b[^>]*>.*?</table>|<br\s*/?>)""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
            tokens.findAll(html).forEach { token ->
                val value = token.value
                if (Regex("""^<h[1-4]\b""", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
                    if (currentTitle.isNotBlank() || currentBlocks.isNotEmpty()) result += LegalSection(currentTitle, currentBlocks.toList())
                    currentTitle = plainText(value).trim()
                    currentBlocks = mutableListOf()
                } else parseBlocks(value).forEach(currentBlocks::add)
            }
            if (currentTitle.isNotBlank() || currentBlocks.isNotEmpty()) result += LegalSection(currentTitle, currentBlocks.toList())
            result.filter { it.title.isNotBlank() || it.blocks.isNotEmpty() }
        } else emptyList()
        return LegalDocument(title, sections, if (sections.isEmpty()) blocks else emptyList(), contentHtml)
    }

    private fun parseBlocks(html: String): List<LegalBlock> {
        val result = mutableListOf<LegalBlock>()
        val tokenRegex = Regex("""(<ol\b[^>]*>.*?</ol>|<ul\b[^>]*>.*?</ul>|<table\b[^>]*>.*?</table>|<p\b[^>]*>.*?</p>|<div\b[^>]*>.*?</div>|<h[1-4]\b[^>]*>.*?</h[1-4]>)""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        tokenRegex.findAll(html).forEach { match ->
            val node = match.value
            when {
                Regex("""^<ol\b""", RegexOption.IGNORE_CASE).containsMatchIn(node) -> {
                    val items = Regex("""<li\b[^>]*>(.*?)</li>""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL).findAll(node).map { inline(it.groupValues[1]) }.toList()
                    if (items.isNotEmpty()) result += LegalBlock.OrderedList(items)
                }
                Regex("""^<ul\b""", RegexOption.IGNORE_CASE).containsMatchIn(node) -> {
                    val items = Regex("""<li\b[^>]*>(.*?)</li>""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL).findAll(node).map { inline(it.groupValues[1]) }.toList()
                    if (items.isNotEmpty()) result += LegalBlock.UnorderedList(items)
                }
                Regex("""^<table\b""", RegexOption.IGNORE_CASE).containsMatchIn(node) -> {
                    val rows = Regex("""<tr\b[^>]*>(.*?)</tr>""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL).findAll(node).mapNotNull { row ->
                        val cells = Regex("""<(?:td|th)\b[^>]*>(.*?)</(?:td|th)>""", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).map { inline(it.groupValues[1]) }.toList()
                        if (cells.size >= 2) cells[0] to cells.drop(1).joinToString(" · ") { it.text }.let { LegalTextBlock(it) } else null
                    }.toList()
                    if (rows.isNotEmpty()) result += LegalBlock.KeyValueTable(rows)
                    else inline(node).takeIf { it.text.isNotBlank() }?.let { result += LegalBlock.Paragraph(it) }
                }
                else -> inline(node).takeIf { it.text.isNotBlank() }?.let { result += LegalBlock.Paragraph(it) }
            }
        }
        if (result.isEmpty()) inline(html).takeIf { it.text.isNotBlank() }?.let { result += LegalBlock.Paragraph(it) }
        return result
    }

    private fun inline(source: String): LegalTextBlock {
        val output = StringBuilder()
        val links = mutableListOf<LegalInlineLink>()
        val strong = mutableListOf<IntRange>()
        val anchorStack = mutableListOf<Pair<Int, String>>()
        val strongStack = mutableListOf<Int>()
        val tags = Regex("""<(/?)([A-Za-z0-9]+)\b([^>]*)>|([^<]+)""", RegexOption.DOT_MATCHES_ALL)
        tags.findAll(source).forEach { m ->
            val tag = m.groupValues[2].lowercase()
            val closing = m.groupValues[1] == "/"
            if (tag.isEmpty()) {
                output.append(legalDecodeEntities(m.groupValues[4]))
            } else when (tag) {
                "a" -> if (!closing) {
                    val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(m.groupValues[3])?.groupValues?.get(1)
                    if (href != null) anchorStack.add(output.length to href)
                } else if (anchorStack.isNotEmpty()) {
                    val (start, url) = anchorStack.removeAt(anchorStack.lastIndex)
                    if (output.length > start) links += LegalInlineLink(start, output.length, url)
                }
                "strong", "b" -> if (!closing) strongStack.add(output.length) else if (strongStack.isNotEmpty()) {
                    val start = strongStack.removeAt(strongStack.lastIndex)
                    if (output.length > start) strong += start until output.length
                }
                "br" -> output.append('\n')
                "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "tr" -> if (closing) output.append('\n')
                "td", "th" -> if (closing) output.append("  |  ")
            }
        }
        val normalized = output.toString().replace("\u00A0", " ").replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
        return LegalTextBlock(normalized, links, strong)
    }
    private fun plainText(source: String) = inline(source).text
}
