package es.criosrango.shared

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke

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
