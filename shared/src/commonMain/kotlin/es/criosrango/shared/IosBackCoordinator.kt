package es.criosrango.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateListOf

internal enum class IosBackPriority(val order: Int) { ROOT(10), CART(20), CATALOG(30), CHECKOUT(40), ACCOUNT(50), OVERLAY(100) }
internal class IosBackCoordinator {
    private data class Entry(val id: Long, val priority: IosBackPriority, val canGoBack: () -> Boolean, val onBack: () -> Unit)
    private val entries = mutableStateListOf<Entry>()
    private var nextId = 0L
    fun register(priority: IosBackPriority, canGoBack: () -> Boolean, onBack: () -> Unit): () -> Unit {
        val id = ++nextId
        entries.add(Entry(id, priority, canGoBack, onBack))
        return { entries.removeAll { it.id == id } }
    }
    fun dispatchBack() {
        val active = entries.maxWithOrNull(compareBy<Entry> { it.priority.order }.thenBy { it.id }) ?: return
        if (active.canGoBack()) active.onBack()
    }
}
internal val LocalIosBackCoordinator = compositionLocalOf<IosBackCoordinator?> { null }
@Composable
internal fun IosBackHandler(priority: IosBackPriority, canGoBack: Boolean = true, onBack: () -> Unit) {
    val coordinator = LocalIosBackCoordinator.current ?: return
    val canGoBackState = rememberUpdatedState(canGoBack)
    val onBackState = rememberUpdatedState(onBack)
    DisposableEffect(coordinator, priority) {
        val dispose = coordinator.register(priority, { canGoBackState.value }, { onBackState.value() })
        onDispose { dispose() }
    }
}
