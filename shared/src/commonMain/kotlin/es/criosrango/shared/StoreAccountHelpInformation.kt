package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore

enum class StoreAccountHelpContactKind { WHATSAPP, PHONE, EMAIL }
data class StoreAccountHelpContact(val title: String, val value: String, val kind: StoreAccountHelpContactKind)
data class StoreAccountBusinessHours(val day: String, val hours: String)

/** Single source of visible Help copy and order for both platforms. */
val STORE_ACCOUNT_HELP_CONTACTS = listOf(
    StoreAccountHelpContact("WhatsApp", "633 246 788", StoreAccountHelpContactKind.WHATSAPP),
    StoreAccountHelpContact("Llamar", "969 091 236", StoreAccountHelpContactKind.PHONE),
    StoreAccountHelpContact("Correo electrónico", "criosrango@criosrango.es", StoreAccountHelpContactKind.EMAIL)
)

/** Android golden copy/order, shared unchanged with iOS. */
val STORE_ACCOUNT_BUSINESS_HOURS = listOf(
    StoreAccountBusinessHours("Lunes", "10:00–13:30 · 17:30–21:00"),
    StoreAccountBusinessHours("Martes", "10:00–13:30 · 17:30–21:00"),
    StoreAccountBusinessHours("Miércoles", "10:00–13:30 · 17:30–21:00"),
    StoreAccountBusinessHours("Jueves", "10:00–13:30 · 17:30–21:00"),
    StoreAccountBusinessHours("Viernes", "10:00–13:30 · 17:30–21:00"),
    StoreAccountBusinessHours("Sábado", "10:00–13:30"),
    StoreAccountBusinessHours("Domingo", "Cerrado")
)

@Composable
fun StoreAccountHelpScreen(
    onBack: () -> Unit,
    onWhatsApp: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        // Keep the validated root-page header geometry unchanged.
        StorePageHeader(title = "Ayuda", onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Contacta con nosotros",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF183B35)
            )
            Spacer(Modifier.height(12.dp))
            STORE_ACCOUNT_HELP_CONTACTS.forEachIndexed { index, contact ->
                val action = when (contact.kind) {
                    StoreAccountHelpContactKind.WHATSAPP -> onWhatsApp
                    StoreAccountHelpContactKind.PHONE -> onCall
                    StoreAccountHelpContactKind.EMAIL -> onEmail
                }
                StoreAccountHelpContactCard(contact, action)
                if (index != STORE_ACCOUNT_HELP_CONTACTS.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Horario de atención",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF183B35)
            )
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE3E7E4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    STORE_ACCOUNT_BUSINESS_HOURS.forEachIndexed { index, row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                row.day,
                                modifier = Modifier.width(72.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF26332D)
                            )
                            Text(
                                row.hours,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4E5A53)
                            )
                        }
                        if (index != STORE_ACCOUNT_BUSINESS_HOURS.lastIndex) {
                            HorizontalDivider(color = Color(0xFFE9ECE9), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreAccountHelpContactCard(contact: StoreAccountHelpContact, onClick: () -> Unit) {
    val icon: ImageVector = when (contact.kind) {
        StoreAccountHelpContactKind.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat
        StoreAccountHelpContactKind.PHONE -> Icons.Outlined.Phone
        StoreAccountHelpContactKind.EMAIL -> Icons.Outlined.Email
    }
    val background = when (contact.kind) {
        StoreAccountHelpContactKind.WHATSAPP -> Color(0xFFEAF4EF)
        StoreAccountHelpContactKind.PHONE -> Color(0xFFFBF4E2)
        StoreAccountHelpContactKind.EMAIL -> Color(0xFFF1EDFA)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF1F6B42),
                modifier = Modifier.size(27.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    contact.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF202923)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    contact.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4E5A53)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color(0xFF52635A)
            )
        }
    }
}

/** Shared renderer for server-authored legal pages. Intro copy is presentation-only. */
@Composable
fun StoreAccountInformationScreen(
    title: String,
    loading: Boolean,
    errorMessage: String?,
    content: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    pageSlug: String = "",
    modifier: Modifier = Modifier
) {
    val parsed = remember(title, content) { LegalDocumentParser.parse(title, content) }
    val intro = when (pageSlug) {
        "cambios-y-devoluciones" -> "Cómo cancelar, cambiar o devolver tu pedido."
        "condiciones-generales-de-contratacion" -> "Condiciones generales de compra en Críos & Rango."
        "politica-de-privacidad" -> "Cómo tratamos y protegemos tus datos personales."
        "aviso-legal" -> "Información legal y datos identificativos del sitio y la empresa."
        "politica-de-cookies" -> "Qué son las cookies, para qué las usamos y cómo puedes gestionarlas."
        else -> ""
    }
    Column(modifier.fillMaxSize()) {
        StorePageHeader(title = title, onBack = onBack)
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            errorMessage != null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMessage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7F1)), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = Color(0xFF176B4A), modifier = Modifier.size(25.dp))
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF183B35))
                        }
                        if (intro.isNotBlank()) Text(intro, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF344C42))
                    }
                }
                Spacer(Modifier.height(18.dp))
                val normalizedTitle = normalizeLegalTitle(parsed.title)
                val visibleSections = parsed.sections.filter { it.title.isNotBlank() && normalizeLegalTitle(it.title) != normalizedTitle }
                val sections = visibleSections
                val bodyBlocks = parsed.sections.filter { it.title.isBlank() || normalizeLegalTitle(it.title) == normalizedTitle }.flatMap { it.blocks }
                val continuousBlocks = buildList<LegalBlock> {
                    addAll(bodyBlocks)
                    visibleSections.forEach { section ->
                        add(LegalBlock.Heading(section.level, LegalTextBlock(section.title)))
                        addAll(section.blocks)
                    }
                    if (parsed.sections.isEmpty()) addAll(parsed.continuousBlocks.filterNot { block ->
                        when (block) {
                            is LegalBlock.Heading -> isDuplicateLegalTitle(block.inline.text, parsed.title)
                            is LegalBlock.Paragraph -> isDuplicateLegalTitle(block.inline.text, parsed.title) && block.inline.strongRanges.any { it.first == 0 && it.last >= block.inline.text.lastIndex }
                            else -> false
                        }
                    })
                }
                val accordionMode = pageSlug == "condiciones-generales-de-contratacion" && sections.size >= 2
                if (accordionMode) {
                    if (bodyBlocks.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { bodyBlocks.forEach { LegalBlockView(it) }; Spacer(Modifier.height(4.dp)) }
                    sections.forEachIndexed { index, section ->
                        var expanded by remember(parsed.rawHtml, index) { mutableStateOf(index == 0) }
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE6EBE7)), elevation = CardDefaults.cardElevation(0.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(section.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF21382E))
                                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Contraer" else "Expandir", tint = Color(0xFF26724F))
                                }
                                if (expanded) Column(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    section.blocks.forEach { LegalBlockView(it) }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        continuousBlocks.forEach { LegalBlockView(it) }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun LegalBlockView(block: LegalBlock) {
    when (block) {
        is LegalBlock.Paragraph -> LegalInlineText(block.inline)
        is LegalBlock.Heading -> Text(block.inline.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF21382E))
        is LegalBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { block.items.forEachIndexed { i, item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${i + 1}.", color = Color(0xFF26724F), fontWeight = FontWeight.SemiBold); LegalInlineText(item) } } }
        is LegalBlock.UnorderedList -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { block.items.forEach { item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•", color = Color(0xFF26724F)); LegalInlineText(item) } } }
        is LegalBlock.KeyValueTable -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { block.rows.forEach { (key, value) -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { LegalInlineText(key, baseWeight = FontWeight.SemiBold); LegalInlineText(value); HorizontalDivider(color = Color(0xFFE8ECE8)) } } }
    }
}

@Composable
private fun LegalInlineText(inline: LegalTextBlock, baseWeight: FontWeight = FontWeight.Normal) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(inline) {
        buildAnnotatedString {
            append(inline.text)
            inline.strongRanges.forEach { range ->
                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), range.first, (range.last + 1).coerceAtMost(length))
            }
            inline.links.forEach { link ->
                val start = link.start.coerceIn(0, length)
                val end = link.end.coerceIn(start, length)
                if (end > start) {
                    addStyle(SpanStyle(color = Color(0xFF176B9A), textDecoration = TextDecoration.Underline), start, end)
                    addStringAnnotation("LEGAL_URL", link.url, start, end)
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp, color = Color(0xFF29352E), fontWeight = baseWeight),
        onClick = { offset ->
            annotated.getStringAnnotations("LEGAL_URL", offset, offset).firstOrNull()?.let { annotation ->
                runCatching { uriHandler.openUri(annotation.item) }
            }
        }
    )
}
