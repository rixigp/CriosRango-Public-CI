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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            mutableState.value = StoreOrdersState(loading = true, authenticatedUserId = userId)
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
                if (requestGeneration == generation && sessionState.snapshot.value.user?.id == userId) {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = if (repository.hasSession) "No se han podido cargar los pedidos." else null
                    )
                }
                if (!repository.hasSession) throw IllegalStateException("La sesión de cuenta ha caducado.")
            }
        } finally {
            loadMutex.unlock()
        }
    }

    suspend fun refresh() = load(force = true)

    fun hasActiveSession(): Boolean = repository.hasSession

    fun findById(orderId: Int): AccountOrderSummary? =
        mutableState.value.orders.firstOrNull { it.id == orderId }

    fun resolveSelection(orderId: Int): StoreOrderSelectionResolution =
        resolveStoreOrderSelection(orderId, mutableState.value, loadedForUser == mutableState.value.authenticatedUserId)

    suspend fun resolveOrderById(orderId: Int): StoreOrderSelectionResolution {
        if (orderId <= 0) return StoreOrderSelectionResolution.NotFound
        findById(orderId)?.let { return StoreOrderSelectionResolution.Resolved(it) }
        val before = mutableState.value
        if (before.loading || before.refreshing) return StoreOrderSelectionResolution.Pending
        load(force = before.error != null)
        return resolveSelection(orderId)
    }

    suspend fun claimPendingOrderAndRefresh(): es.criosrango.shared.account.AccountClaimOrderResponse? {
        return claimAndRefreshIfSuccessful(
            claim = { repository.claimPendingOrder() },
            refresh = { refresh() }
        )
    }
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(auth.user?.id) {
        owner.onSessionChanged(auth.user?.id)
        if (auth.user != null) {
            try { owner.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!owner.hasActiveSession()) onSessionExpired() }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        StorePageHeader(title = "Pedidos", onBack = onBack, horizontalPadding = 0.dp)
        when {
            auth.user == null -> Unit
            state.authenticatedUserId != auth.user?.id -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.loading && state.orders.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.orders.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { scope.launch { try { owner.refresh() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (!owner.hasActiveSession()) onSessionExpired() } } }) { Text("Reintentar") }
            }
            state.orders.isEmpty() -> Text("Todavía no tienes pedidos.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
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
    val address = order.shippingAddress
    val province = storeProvinceName(address.state)
    val city = if (address.city.equals("tarancon", true)) "Tarancón" else address.city
    val hasAddress = listOf(address.address1, address.address2, address.city, address.postcode, address.country).any { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            StorePageHeader(title = "Detalle de pedido", onBack = onBack, horizontalPadding = 0.dp)
        }
        item {
            StoreOrderDetailCard(title = "Pedido #${order.number.ifBlank { order.id.toString() }}") {
                StoreOrderStatusBadge(order.status, order.statusLabel)
                val date = formatStoreOrderDate(order.dateCreated.orEmpty())
                if (date.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Fecha: $date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (order.total.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatStoreAmount(order.total, order.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            StoreOrderDetailCard(title = "Productos (${order.items.size})") {
                if (order.items.isEmpty()) {
                    Text("No hay productos disponibles.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    order.items.forEachIndexed { index, item ->
                        if (index > 0) Surface(modifier = Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                        Text(cleanStoreWooText(item.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val variations = storeOrderVariations(item)
                        if (variations.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(variations, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("Cantidad: ${item.quantity}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item {
            StoreOrderDetailCard(title = "Entrega") {
                if (!hasAddress) {
                    Text("No hay dirección de entrega disponible.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (address.address1.isNotBlank()) Text(cleanStoreWooText(address.address1), style = MaterialTheme.typography.bodyLarge)
                    if (address.address2.isNotBlank()) Text(cleanStoreWooText(address.address2), style = MaterialTheme.typography.bodyMedium)
                    val location = listOf(address.postcode, city, province).filter { it.isNotBlank() }.joinToString(" · ")
                    if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium)
                    if (address.country.isNotBlank()) Text(if (address.country.equals("ES", true)) "España" else address.country, style = MaterialTheme.typography.bodyMedium)
                }
                if (order.shippingMethod.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Método de envío", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cleanStoreWooText(order.shippingMethod), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            StoreOrderDetailCard(title = "Pago") {
                val payment = storeFriendlyPaymentMethod(order.paymentMethodTitle, order.paymentMethod)
                if (payment.isNotBlank()) Text(payment, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            StoreOrderDetailCard(title = "Resumen") {
                if (order.subtotal.isNotBlank()) {
                    StoreOrderSummaryLine("Productos", formatStoreAmount(order.subtotal, order.currency))
                }
                if (order.shippingTotal.isNotBlank()) {
                    StoreOrderSummaryLine("Envío", formatStoreAmount(order.shippingTotal, order.currency))
                }
                Surface(modifier = Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                if (order.total.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatStoreAmount(order.total, order.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (storeOrderIsReturnEligible(order.status, order.statusLabel)) {
            item {
                StoreOrderDetailCard(title = "Devolución") {
                    Text("¿Necesitas devolver algo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onRequestReturn(order) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Solicitar devolución")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreOrderDetailCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StoreOrderSummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun storeFriendlyPaymentMethod(title: String, method: String): String {
    val source = "$title $method".trim()
    if (source.isBlank()) return ""
    return when {
        source.contains("bizum", true) -> "Bizum"
        source.contains("tarjeta", true) || source.contains("card", true) ||
            source.contains("credit", true) || source.contains("debit", true) ||
            source.contains("ceca", true) || source.contains("redsys", true) ||
            source.contains("stripe", true) -> "Tarjeta"
        title.isNotBlank() && title.any { it.isWhitespace() } -> cleanStoreWooText(title)
        else -> "Pago"
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

internal fun storeOrderMinorUnits(value: String): Long {
    val raw = value.trim().replace(",", ".")
    if (raw.isBlank()) return 0L
    val negative = raw.startsWith("-")
    val unsigned = raw.removePrefix("-").removePrefix("+")
    val parts = unsigned.split(".", limit = 2)
    val whole = parts[0].filter { it in '0'..'9' }.toLongOrNull() ?: return 0L
    val fraction = parts.getOrElse(1) { "" }.filter { it in '0'..'9' }
    val firstTwo = (fraction + "00").take(2).toLongOrNull() ?: 0L
    val rounded = fraction.length > 2 && fraction[2] >= '5'
    val cents = whole * 100L + firstTwo + if (rounded) 1L else 0L
    return if (negative) -cents else cents
}

internal fun formatStoreMinorUnits(value: Long, currency: String): String {
    val absolute = kotlin.math.abs(value)
    val amount = (if (value < 0) "-" else "") + (absolute / 100L).toString() + "," + (absolute % 100L).toString().padStart(2, '0')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}

private fun formatStoreAmount(value: String, currency: String): String {
    val amount = value.replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
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

