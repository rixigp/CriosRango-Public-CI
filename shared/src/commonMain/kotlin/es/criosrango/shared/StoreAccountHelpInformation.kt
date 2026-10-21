package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        StorePageHeader(title = "Ayuda", onBack = onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            Text("Contacta con nosotros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35))
            Spacer(Modifier.height(16.dp))
            STORE_ACCOUNT_HELP_CONTACTS.forEachIndexed { index, contact ->
                StoreAccountHelpContactRow(contact, when (contact.kind) {
                    StoreAccountHelpContactKind.WHATSAPP -> onWhatsApp
                    StoreAccountHelpContactKind.PHONE -> onCall
                    StoreAccountHelpContactKind.EMAIL -> onEmail
                })
                if (index != STORE_ACCOUNT_HELP_CONTACTS.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(28.dp))
            Text("Horario de atención", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35))
            Spacer(Modifier.height(14.dp))
            STORE_ACCOUNT_BUSINESS_HOURS.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                    Text(row.day, modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(row.hours, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoreAccountHelpContactRow(contact: StoreAccountHelpContact, onClick: () -> Unit) {
    val icon: ImageVector = when (contact.kind) {
        StoreAccountHelpContactKind.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat
        StoreAccountHelpContactKind.PHONE -> Icons.Outlined.Phone
        StoreAccountHelpContactKind.EMAIL -> Icons.Outlined.Email
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Color(0xFFE3EFEB), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(contact.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shared information-page renderer. Network loading and retry stay in platform adapters. */
@Composable
fun StoreAccountInformationScreen(
    title: String,
    loading: Boolean,
    errorMessage: String?,
    content: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        StorePageHeader(title = title, onBack = onBack)
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage != null -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMessage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                Text(content, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
