package es.criosrango.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.StoreAccountInformationScreen
import es.criosrango.shared.LegalDocumentRepository


enum class AccountInfoPage(val title: String, val slug: String) {
    RETURNS("Cambios y devoluciones", "cambios-y-devoluciones"),
    TERMS("Condiciones de contratación", "condiciones-generales-de-contratacion"),
    PRIVACY("Política de privacidad", "politica-de-privacidad"),
    LEGAL("Aviso legal", "aviso-legal"),
    COOKIES("Política de cookies", "politica-de-cookies")
}

sealed interface InfoPageUiState {
    data object Loading : InfoPageUiState
    data class Success(val title: String, val content: String) : InfoPageUiState
    data class Error(val message: String) : InfoPageUiState
}

@Composable
private fun rememberInfoPageState(page: AccountInfoPage): Pair<InfoPageUiState, () -> Unit> {
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember(page, retryKey) { mutableStateOf<InfoPageUiState>(LegalDocumentRepository.peek(page.slug)?.let { InfoPageUiState.Success(it.title, it.rawHtml) } ?: InfoPageUiState.Loading) }
    LaunchedEffect(page, retryKey) {
        if (LegalDocumentRepository.peek(page.slug) == null) state = InfoPageUiState.Loading
        state = try {
            val entry = LegalDocumentRepository.load(page.slug)
            if (entry == null) InfoPageUiState.Error("No se ha podido cargar esta información.")
            else InfoPageUiState.Success(title = entry.title, content = entry.rawHtml)
        } catch (_: Exception) {
            InfoPageUiState.Error("No se ha podido cargar esta información.")
        }
    }
    val updates by LegalDocumentRepository.updates.collectAsState()
    LaunchedEffect(page.slug, updates[page.slug]) {
        updates[page.slug]?.let { entry -> state = InfoPageUiState.Success(entry.title, entry.rawHtml) }
    }
    return state to { retryKey++ }
}

@Composable
fun AccountInformationPageAdapter(page: AccountInfoPage, onBack: () -> Unit) {
    val (state, retry) = rememberInfoPageState(page)
    BackHandler(onBack = onBack)
    StoreAccountInformationScreen(
        title = page.title,
        loading = state is InfoPageUiState.Loading,
        errorMessage = (state as? InfoPageUiState.Error)?.message,
        content = (state as? InfoPageUiState.Success)?.content.orEmpty(),
        pageSlug = page.slug,
        onRetry = retry,
        onBack = onBack
    )
}

@Composable
fun AccountInformationRow(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun AccountHomeCardV2(
    modifier: Modifier = Modifier,
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides Color(0xFF183B35)) {
                icon()
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AccountInformationHomeSection(onInfoPage: (AccountInfoPage) -> Unit) {
    Text("Información", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    AccountInformationRow("Cambios y devoluciones", { Icon(Icons.Outlined.Restore, contentDescription = null) }) { onInfoPage(AccountInfoPage.RETURNS) }
    AccountInformationRow("Condiciones de contratación", { Icon(Icons.Outlined.Description, contentDescription = null) }) { onInfoPage(AccountInfoPage.TERMS) }
    AccountInformationRow("Política de privacidad", { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) }) { onInfoPage(AccountInfoPage.PRIVACY) }
    AccountInformationRow("Aviso legal", { Icon(Icons.Outlined.Description, contentDescription = null) }) { onInfoPage(AccountInfoPage.LEGAL) }
    AccountInformationRow("Política de cookies", { Icon(Icons.Outlined.Cookie, contentDescription = null) }) { onInfoPage(AccountInfoPage.COOKIES) }
}
