package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountOrderItem
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.util.Locale

data class StoreOrdersState(
    val loading: Boolean = false,
    val orders: List<AccountOrderSummary> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val authenticatedUserId: Int? = null
)

class StoreOrdersOwner(
    private val repository: AccountRepository,
    private val sessionState: StoreAccountSessionState
) {
    private val mutableState = MutableStateFlow(StoreOrdersState())
    val state: StateFlow<StoreOrdersState> = mutableState.asStateFlow()
    private val loadMutex = Mutex()
    private var generation = 0L
    private var loadedForUser: Int? = null

    fun onSessionChanged(userId: Int?) {
        if (userId == null || !repository.hasSession) {
            generation++
            loadedForUser = null
            mutableState.value = StoreOrdersState()
        } else if (mutableState.value.authenticatedUserId != userId) {
            generation++
            loadedForUser = null
            mutableState.value = StoreOrdersState(authenticatedUserId = userId)
        }
    }

    suspend fun load(force: Boolean = false) {
        val userId = sessionState.snapshot.value.user?.id
        if (userId == null || !repository.hasSession) {
            onSessionChanged(null)
            return
        }
        if (!force && loadedForUser == userId && mutableState.value.error == null) return
        if (!loadMutex.tryLock()) return
        val requestGeneration = generation
        try {
            if (sessionState.snapshot.value.user?.id != userId || !repository.hasSession) return
            mutableState.value = mutableState.value.copy(
                loading = mutableState.value.orders.isEmpty(),
                refreshing = mutableState.value.orders.isNotEmpty(),
                error = null,
                authenticatedUserId = userId
            )
            try {
                val response = repository.orders(20).orders
                if (requestGeneration == generation &&
                    repository.hasSession &&
                    sessionState.snapshot.value.user?.id == userId
                ) {
                    mutableState.value = StoreOrdersState(
                        orders = response,
                        loading = false,
                        refreshing = false,
                        authenticatedUserId = userId
                    )
                    loadedForUser = userId
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration == generation) {
                    mutableState.value = mutableState.value.copy(loading = false, refreshing = false, error = null)
                }
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation &&
                    repository.hasSession &&
                    sessionState.snapshot.value.user?.id == userId
                ) {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = "No se han podido cargar los pedidos."
                    )
                }
            }
        } finally {
            loadMutex.unlock()
        }
    }

    suspend fun refresh() = load(force = true)

    fun findById(orderId: Int): AccountOrderSummary? =
        mutableState.value.orders.firstOrNull { it.id == orderId }
}

enum class StoreOrderStatusKind { COMPLETED, PROCESSING, SHIPPED, PENDING, ON_HOLD, CANCELLED, REFUNDED, FAILED, UNKNOWN }

data class StoreOrderStatusStyle(val kind: StoreOrderStatusKind, val label: String, val background: Long, val foreground: Long)

object StoreOrderStatusMapper {
    fun normalize(value: String): String = value.lowercase()
        .replace("á", "a").replace("à", "a").replace("ä", "a")
        .replace("é", "e").replace("è", "e").replace("ë", "e")
        .replace("í", "i").replace("ì", "i").replace("ï", "i")
        .replace("ó", "o").replace("ò", "o").replace("ö", "o")
        .replace("ú", "u").replace("ù", "u").replace("ü", "u")
        .replace("ñ", "n").replace("&", " and ")
        .replace('-', ' ').replace('_', ' ')
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun classify(status: String, label: String): StoreOrderStatusKind {
        val value = normalize("$status $label")
        fun has(vararg candidates: String) = candidates.any { value == it || value.contains(" $it ") || value.startsWith("$it ") || value.endsWith(" $it") }
        return when {
            has("completed", "completado") -> StoreOrderStatusKind.COMPLETED
            has("processing", "procesando", "preparacion") -> StoreOrderStatusKind.PROCESSING
            has("shipped", "enviado") -> StoreOrderStatusKind.SHIPPED
            has("pending", "pending payment", "pendiente") -> StoreOrderStatusKind.PENDING
            has("on hold", "en espera") -> StoreOrderStatusKind.ON_HOLD
            has("cancelled", "canceled", "cancelado") -> StoreOrderStatusKind.CANCELLED
            has("refunded", "reembolsado") -> StoreOrderStatusKind.REFUNDED
            has("failed", "fallido") -> StoreOrderStatusKind.FAILED
            else -> StoreOrderStatusKind.UNKNOWN
        }
    }

    fun visibleLabel(status: String, serverLabel: String): String = serverLabel.takeIf { it.isNotBlank() } ?: status

    fun style(status: String, label: String): StoreOrderStatusStyle = when (classify(status, label)) {
        StoreOrderStatusKind.COMPLETED -> StoreOrderStatusStyle(StoreOrderStatusKind.COMPLETED, visibleLabel(status, label), 0xFFE3F1E8, 0xFF285C3B)
        StoreOrderStatusKind.PROCESSING -> StoreOrderStatusStyle(StoreOrderStatusKind.PROCESSING, visibleLabel(status, label), 0xFFF4EAD3, 0xFF76591F)
        StoreOrderStatusKind.SHIPPED -> StoreOrderStatusStyle(StoreOrderStatusKind.SHIPPED, visibleLabel(status, label), 0xFFE4EFF8, 0xFF245579)
        StoreOrderStatusKind.PENDING -> StoreOrderStatusStyle(StoreOrderStatusKind.PENDING, visibleLabel(status, label), 0xFFFFF4D8, 0xFF765A16)
        StoreOrderStatusKind.ON_HOLD -> StoreOrderStatusStyle(StoreOrderStatusKind.ON_HOLD, visibleLabel(status, label), 0xFFECE9E3, 0xFF5E5A53)
        StoreOrderStatusKind.CANCELLED -> StoreOrderStatusStyle(StoreOrderStatusKind.CANCELLED, visibleLabel(status, label), 0xFFF8E3E1, 0xFF8A332D)
        StoreOrderStatusKind.REFUNDED -> StoreOrderStatusStyle(StoreOrderStatusKind.REFUNDED, visibleLabel(status, label), 0xFFEDE7F3, 0xFF5A476D)
        StoreOrderStatusKind.FAILED -> StoreOrderStatusStyle(StoreOrderStatusKind.FAILED, visibleLabel(status, label), 0xFFF6E0DE, 0xFF8A332D)
        StoreOrderStatusKind.UNKNOWN -> StoreOrderStatusStyle(StoreOrderStatusKind.UNKNOWN, visibleLabel(status, label), 0xFFE9E9E7, 0xFF5C5C58)
    }
}

@Composable
fun StoreOrderStatusBadge(status: String, label: String) {
    val style = StoreOrderStatusMapper.style(status, label)
    Surface(shape = RoundedCornerShape(50), color = Color(style.background), contentColor = Color(style.foreground)) {
        Text(style.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
    }
}

@Composable
fun StoreOrdersScreen(
    owner: StoreOrdersOwner,
    sessionState: StoreAccountSessionState,
    onBack: () -> Unit,
    onOpenOrder: (AccountOrderSummary) -> Unit,
    onSessionExpired: () -> Unit
) {
    val state by owner.state.collectAsState()
    val auth by sessionState.snapshot.collectAsState()
    LaunchedEffect(auth.user?.id) {
        owner.onSessionChanged(auth.user?.id)
        if (auth.user != null) {
            try { owner.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!sessionState.snapshot.value.user.let { it != null }) onSessionExpired() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        StoreAccountSectionHeader("Pedidos", onBack)
        when {
            auth.user == null -> Unit
            state.loading && state.orders.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.orders.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { kotlinx.coroutines.GlobalScope /* no-op */ }) { Text("Reintentar") }
            }
            state.orders.isEmpty() -> Text("Todavía no tienes pedidos.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.orders, key = { it.id }) { order ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenOrder(order) }, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Pedido #${order.number.ifBlank { order.id.toString() }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            StoreOrderStatusBadge(order.status, order.statusLabel)
                            val date = formatStoreOrderDate(order.dateCreated.orEmpty())
                            if (date.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Fecha: $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            order.items.forEach { item ->
                                Spacer(Modifier.height(8.dp))
                                Text("${item.quantity} × ${cleanStoreWooText(item.name)}", style = MaterialTheme.typography.bodyMedium)
                                val vars = storeOrderVariations(item)
                                if (vars.isNotBlank()) Text(vars, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (order.paymentMethodTitle.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta", style = MaterialTheme.typography.bodySmall) }
                            if (order.total.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Total: ${formatStoreAmount(order.total, order.currency)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreOrderDetailScreen(
    order: AccountOrderSummary,
    onBack: () -> Unit,
    onRequestReturn: (AccountOrderSummary) -> Unit
) {
    val a = order.shippingAddress
    val province = storeProvinceName(a.state)
    val city = if (a.city.equals("tarancon", true)) "Tarancón" else a.city
    val shipping = storeDecimal(order.shippingTotal)
    val total = storeDecimal(order.total)
    val products = (total - shipping).coerceAtLeast(BigDecimal.ZERO)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            StoreAccountSectionHeader("Pedido #${order.number}", onBack)
            Spacer(Modifier.height(8.dp))
            StoreOrderStatusBadge(order.status, order.statusLabel)
            val date = formatStoreOrderDate(order.dateCreated.orEmpty())
            if (date.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("Fecha: $date") }
            Spacer(Modifier.height(24.dp))
        }
        item {
            StoreOrderDetailCard("Productos") {
                order.items.forEach { item ->
                    Text("${item.quantity} × ${cleanStoreWooText(item.name)}")
                    val vars = storeOrderVariations(item)
                    if (vars.isNotBlank()) Text(vars, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { StoreOrderDetailCard("Pago") { Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta") } }
        if (a.address1.isNotBlank() || a.city.isNotBlank() || a.postcode.isNotBlank()) item {
            StoreOrderDetailCard("Entrega") {
                if (a.address1.isNotBlank()) Text(a.address1)
                val location = listOf(a.postcode, city, province).filter { it.isNotBlank() }.joinToString(" · ")
                if (location.isNotBlank()) Text(location)
                if (a.country.isNotBlank()) Text(if (a.country == "ES") "España" else a.country)
                if (order.shippingMethod.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (order.shippingMethod.startsWith("Envío", true)) cleanStoreWooText(order.shippingMethod) else "Envío: ${cleanStoreWooText(order.shippingMethod)}")
                }
            }
        }
        item {
            StoreOrderDetailCard("Resumen") {
                Text("Productos: ${formatStoreDecimal(products, order.currency)}")
                Text(if (shipping.compareTo(BigDecimal.ZERO) == 0) "Envío: Gratis" else "Envío: ${formatStoreAmount(order.shippingTotal, order.currency)}")
                Spacer(Modifier.height(8.dp))
                Text("Total: ${formatStoreAmount(order.total, order.currency)}", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (storeCanRequestReturn(order.status, order.statusLabel)) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text("¿Necesitas devolver algo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF454145))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onRequestReturn(order) }, modifier = Modifier.fillMaxWidth()) { Text("Solicitar devolución") }
            }
        }
    }
}

@Composable
private fun StoreOrderDetailCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = if (title == "Resumen") MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun storeOrderVariations(item: AccountOrderItem): String = item.variations
    .filter { it.name.isNotBlank() && it.value.isNotBlank() }
    .joinToString(" · ") { "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${cleanStoreWooText(it.value)}" }

private fun cleanStoreWooText(value: String): String = value
    .replace("&amp;", "&").replace("&#038;", "&").replace("&quot;", "\"")
    .replace("&#8217;", "’").replace("&rsquo;", "’").replace("&nbsp;", " ").trim()

private fun formatStoreOrderDate(value: String): String {
    val raw = value.take(10).split("-")
    return if (raw.size == 3 && raw[0].length == 4) "${raw[2]}-${raw[1]}-${raw[0]}" else value.take(10)
}

private fun storeDecimal(value: String): BigDecimal = try {
    BigDecimal(value.trim().replace(",", "."))
} catch (_: Exception) { BigDecimal.ZERO }

private fun formatStoreDecimal(value: BigDecimal, currency: String): String {
    val amount = value.stripTrailingZeros().toPlainString().replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}

private fun formatStoreAmount(value: String, currency: String): String {
    val amount = value.replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}

private fun storeCanRequestReturn(status: String, label: String): Boolean {
    val value = "${StoreOrderStatusMapper.normalize(status)} ${StoreOrderStatusMapper.normalize(label)}"
    val allowed = listOf("processing", "procesando", "preparacion", "completed", "completado", "shipped", "enviado")
    val denied = listOf("cancelled", "canceled", "cancelado", "failed", "fallido", "refunded", "reembolsado", "pending", "pending payment", "pendiente", "on hold", "en espera")
    return allowed.any { value.contains(it) } && denied.none { value.contains(it) }
}

private fun storeProvinceName(code: String): String = listOf(
    "C" to "A Coruña", "VI" to "Araba/Álava", "AB" to "Albacete", "A" to "Alicante", "AL" to "Almería",
    "O" to "Asturias", "AV" to "Ávila", "BA" to "Badajoz", "B" to "Barcelona", "BI" to "Bizkaia",
    "BU" to "Burgos", "CC" to "Cáceres", "CA" to "Cádiz", "S" to "Cantabria", "CS" to "Castellón",
    "CE" to "Ceuta", "CR" to "Ciudad Real", "CO" to "Córdoba", "CU" to "Cuenca", "GI" to "Girona",
    "GR" to "Granada", "GU" to "Guadalajara", "SS" to "Gipuzkoa", "H" to "Huelva", "HU" to "Huesca",
    "J" to "Jaén", "LE" to "León", "L" to "Lleida", "LO" to "La Rioja", "LU" to "Lugo",
    "M" to "Madrid", "MA" to "Málaga", "ML" to "Melilla", "MU" to "Murcia", "NA" to "Navarra",
    "OR" to "Ourense", "P" to "Palencia", "GC" to "Las Palmas", "PO" to "Pontevedra", "SA" to "Salamanca",
    "TF" to "Santa Cruz de Tenerife", "SG" to "Segovia", "SE" to "Sevilla", "SO" to "Soria", "T" to "Tarragona",
    "TE" to "Teruel", "TO" to "Toledo", "V" to "Valencia", "VA" to "Valladolid", "ZA" to "Zamora", "Z" to "Zaragoza"
).firstOrNull { it.first.equals(code, true) }?.second ?: code

