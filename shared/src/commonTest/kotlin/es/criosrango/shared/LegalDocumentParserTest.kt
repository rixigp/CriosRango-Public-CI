package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalDocumentParserTest {
    @Test fun semanticHeadingsCreateSections() {
        val doc = LegalDocumentParser.parse("Title", "<h2>Real heading</h2><p>Body</p><h3>Next</h3><ul><li>One</li></ul>")
        assertEquals(2, doc.sections.size)
        assertEquals("Real heading", doc.sections.first().title)
        assertTrue(doc.sections.first().blocks.any { it is LegalBlock.Paragraph })
    }
    @Test fun noHeadingsRemainsContinuous() {
        val doc = LegalDocumentParser.parse("Title", "<p>Intro</p><ol><li>One</li><li>Two</li></ol>")
        assertTrue(doc.sections.isEmpty())
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.OrderedList })
    }
    @Test fun strongIsNotAHeadingAndListsStayTyped() {
        val doc = LegalDocumentParser.parse("Title", "<p><strong>Bold</strong> body</p><ul><li>A</li></ul><ol><li>B</li></ol>")
        val para = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first()
        assertEquals("Bold body", para.inline.text)
        assertTrue(para.inline.strongRanges.isNotEmpty())
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.UnorderedList })
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.OrderedList })
    }
    @Test fun tableLabelsAndValuesStaySeparated() {
        val doc = LegalDocumentParser.parse("Title", "<table><tr><th>Etiqueta</th><td>Valor</td></tr></table>")
        val table = doc.continuousBlocks.filterIsInstance<LegalBlock.KeyValueTable>().first()
        assertEquals("Etiqueta", table.rows.first().first.text)
        assertEquals("Valor", table.rows.first().second.text)
    }
    @Test fun shortcodeAndEntitiesDoNotDiscardInnerContent() {
        val doc = LegalDocumentParser.parse("Title", "[vc_row][nectar_text]Texto &mdash; &raquo; &#65; &#x1F600;[/nectar_text][/vc_row]")
        val text = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first().inline.text
        assertTrue(text.contains("Texto"))
        assertTrue(text.contains("—"))
        assertTrue(text.contains("»"))
        assertTrue(text.contains("A"))
        assertTrue(text.contains("😀"))
    }
    @Test fun linksAreRetained() {
        val doc = LegalDocumentParser.parse("Title", "<p><a href=\"https://example.com\">Link</a></p>")
        val p = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first().inline
        assertEquals("Link", p.text)
        assertEquals("https://example.com", p.links.first().url)
    }
}
