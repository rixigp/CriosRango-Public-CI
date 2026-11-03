package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalDocumentParserTest {
    @Test fun semanticHeadingsCreateSections() {
        val doc = LegalDocumentParser.parse("Title", "<h2>Real heading</h2><p>Body</p><h2>Next</h2><ul><li>One</li></ul>")
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
        val spanish = LegalDocumentParser.parse("Title", "<p>Condici&oacute;n &aacute;rea &oacute;ptima</p>").continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first().inline.text
        assertTrue(spanish.contains("Condición área óptima"))
        val punctuation = LegalDocumentParser.parse("Title", "<p>&iexcl;Qu&eacute; &oacute;ptimo! &iquest;Cu&aacute;l?</p>").continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first().inline.text
        assertTrue(punctuation.contains("¡Qué óptimo! ¿Cuál?"))
    }
    @Test fun looseTextBetweenBuilderWrappersIsNotDropped() {
        val doc = LegalDocumentParser.parse("Title", "<div>before<p>Paragraph</p>after</div>")
        val visible = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().joinToString(" ") { it.inline.text }
        assertTrue(visible.contains("before"))
        assertTrue(visible.contains("Paragraph"))
        assertTrue(visible.contains("after"))
    }
    @Test fun linksAreRetained() {
        val doc = LegalDocumentParser.parse("Title", "<p><a href=\"https://example.com\">Link</a></p>")
        val p = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first().inline
        assertEquals("Link", p.text)
        assertEquals("https://example.com", p.links.first().url)
    }

    @Test fun nonEmptyHtmlAlwaysHasVisibleRenderBlocks() {
        val fixtures = listOf(
            "[vc_row][vc_column][vc_column_text]<p>Política de privacidad real.</p>[/vc_column_text][/vc_column][/vc_row]",
            "[nectar_container][nectar_text]<p>Contenido Nectar.</p>[/nectar_text][/nectar_container]",
            "[salient_section][salient_text]<p>Contenido Salient.</p>[/salient_text][/salient_section]",
            "<div class='wpb_wrapper'><span>Texto envuelto sin párrafo</span></div>"
        )
        fixtures.forEach { html ->
            val doc = LegalDocumentParser.parse("Privacidad", html)
            assertTrue(doc.continuousBlocks.isNotEmpty() || doc.sections.any { it.blocks.isNotEmpty() })
        }
    }
    @Test fun h1ThroughH6UseConsistentStructuralDetection() {
        (1..6).forEach { level ->
            val doc = LegalDocumentParser.parse("Terms", "<h$level>Primero</h$level><p>A</p><h$level>Segundo</h$level><p>B</p>")
            assertEquals(2, doc.sections.size)
            assertTrue(doc.sections.all { it.level == level })
        }
    }
    @Test fun aSingleHeadingDoesNotCreateAccordionSections() {
        val doc = LegalDocumentParser.parse("Returns", "<h3>Información</h3><p>Texto seguido.</p>")
        assertTrue(doc.sections.isEmpty())
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.Heading })
    }
    @Test fun differentHeadingLevelsDoNotCreateInconsistentSections() {
        val doc = LegalDocumentParser.parse("Terms", "<h2>Primero</h2><p>A</p><h3>Segundo</h3><p>B</p>")
        assertTrue(doc.sections.isEmpty())
    }
    @Test fun normalizedDuplicateTitleDoesNotHideFollowingBody() {
        assertEquals(normalizeLegalTitle("Cambios y devoluciones"), normalizeLegalTitle(" Cambios   y devoluciones. "))
        assertTrue(isDuplicateLegalTitle("Cambios y devoluciones.", "Cambios y devoluciones"))
        val doc = LegalDocumentParser.parse("Cambios y devoluciones", "<h2>Cambios y devoluciones.</h2><p>El cuerpo sigue visible.</p>")
        assertTrue(doc.sections.isEmpty())
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.Paragraph && it.inline.text.contains("cuerpo sigue") })
    }
    @Test fun tableAndLinksSurviveAndStrongOnlyParagraphKeepsEmphasis() {
        val doc = LegalDocumentParser.parse("Aviso", "<table><tr><th>IDENTIDAD</th><td>Críos &amp; Rango</td></tr></table><p><strong>Cookies propias</strong></p><p><a href='https://example.com'>Enlace</a></p>")
        assertTrue(doc.continuousBlocks.any { it is LegalBlock.KeyValueTable })
        val strong = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first { it.inline.text == "Cookies propias" }
        assertTrue(strong.inline.strongRanges.isNotEmpty())
        val link = doc.continuousBlocks.filterIsInstance<LegalBlock.Paragraph>().first { it.inline.links.isNotEmpty() }
        assertEquals("https://example.com", link.inline.links.first().url)
    }
    @Test fun lastResortFallbackPreservesTextInsideBuilderWrappers() {
        val html = "[vc_row][vc_column][nectar_text]Texto contractual.[/nectar_text][/vc_column][/vc_row]"
        assertTrue(legalHtmlToPlainText(html).contains("Texto contractual."))
    }

}
