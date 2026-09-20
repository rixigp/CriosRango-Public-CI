package es.criosrango.app

import android.text.Html
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val WORDPRESS_BASE_URL = "https://www.criosrango.es/"

data class WordPressRendered(val rendered: String = "")

data class WordPressPage(
    val id: Long = 0,
    val slug: String = "",
    val title: WordPressRendered = WordPressRendered(),
    val content: WordPressRendered = WordPressRendered()
)

interface WordPressPagesApi {
    @GET("wp-json/wp/v2/pages")
    suspend fun getPageBySlug(@Query("slug") slug: String): List<WordPressPage>
}

private object WordPressPagesClient {
    val api: WordPressPagesApi by lazy {
        Retrofit.Builder()
            .baseUrl(WORDPRESS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WordPressPagesApi::class.java)
    }
}

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

private fun wordpressHtmlToText(
    html: String
): String {

    if (html.isBlank()) return ""

    val withoutShortcodes = html
        .replace(
            Regex(
                """\[(?:/)?vc_[^\]]*]""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex(
                """\[(?:/)?(?:nectar|salient)_[^\]]*]""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex(
                """<li[^>]*>""",
                RegexOption.IGNORE_CASE
            ),
            "• "
        )
        .replace(
            Regex(
                """</li>""",
                RegexOption.IGNORE_CASE
            ),
            "\n"
        )
        .replace(
            Regex(
                """</(?:p|div|h1|h2|h3|h4|h5|h6|ul|ol)>""",
                RegexOption.IGNORE_CASE
            ),
            "\n\n"
        )

    return Html.fromHtml(
        withoutShortcodes,
        Html.FROM_HTML_MODE_LEGACY
    )
        .toString()
        .replace("\u00A0", " ")
        .replace("»", "")
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n[ \t]+"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

@Composable
private fun rememberInfoPageState(page: AccountInfoPage): Pair<InfoPageUiState, () -> Unit> {
    var retryKey by remember { mutableIntStateOf(0) }
    var state by remember(page, retryKey) { mutableStateOf<InfoPageUiState>(InfoPageUiState.Loading) }

    LaunchedEffect(page, retryKey) {
        state = InfoPageUiState.Loading
        state = try {
            val result = WordPressPagesClient.api.getPageBySlug(page.slug).firstOrNull()
            if (result == null) {
                InfoPageUiState.Error("No se ha podido cargar esta información.")
            } else {
                InfoPageUiState.Success(
                    title = wordpressHtmlToText(result.title.rendered).ifBlank { page.title },
                    content = wordpressHtmlToText(result.content.rendered)
                )
            }
        } catch (_: Exception) {
            InfoPageUiState.Error("No se ha podido cargar esta información.")
        }
    }

    return state to { retryKey++ }
}

@Composable
fun AccountInformationPageContent(page: AccountInfoPage, onBack: () -> Unit) {
    val (state, retry) = rememberInfoPageState(page)
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        AccountSectionHeader(title = page.title, onBack = onBack)
        Spacer(Modifier.height(18.dp))
        when (val current = state) {
            InfoPageUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is InfoPageUiState.Error -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = retry) { Text("Reintentar") }
            }

            is InfoPageUiState.Success -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                Text(current.content, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(48.dp))
            }
        }
    }
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
