package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch

/** Account-specific validation. Deliberately independent from checkout's geographic rules. */
object StoreAccountFormValidation {
    val spanishProvinceCodes: Set<String> = setOf(
        "C","VI","AB","A","AL","O","AV","BA","B","BI","BU","CC","CA","S","CS","CE","CR","CO","CU","GI","GR","GU","SS","H","HU","J","LE","L","LO","LU","M","MA","ML","MU","NA","OR","P","GC","PO","SA","TF","SG","SE","SO","T","TE","TO","V","VA","ZA","Z"
    )
    val provinceOptions: List<Pair<String, String>> = listOf(
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
    )

    fun profileError(firstName: String, lastName: String): String? =
        if (firstName.trim().isBlank() || lastName.trim().isBlank()) "Introduce tu nombre y apellidos." else null

    fun mergeProfileNames(address: AccountCustomerAddress, firstName: String, lastName: String): AccountCustomerAddress =
        address.copy(firstName = firstName.trim(), lastName = lastName.trim())

    fun cleanAddress(address: AccountCustomerAddress): AccountCustomerAddress = address.copy(
        firstName = address.firstName.trim(),
        lastName = address.lastName.trim(),
        address1 = address.address1.trim(),
        address2 = address.address2.trim(),
        postcode = address.postcode.trim(),
        city = address.city.trim(),
        state = address.state.trim().uppercase(),
        country = "ES"
    )

    fun addressError(address: AccountCustomerAddress): String? {
        val clean = cleanAddress(address)
        if (clean.firstName.isBlank() || clean.lastName.isBlank() || clean.address1.isBlank() ||
            clean.postcode.isBlank() || clean.city.isBlank() || clean.state.isBlank()
        ) return "Completa todos los campos obligatorios."
        if (clean.postcode.length != 5 || clean.postcode.any { it !in '0'..'9' }) return "Introduce un código postal válido."
        if (clean.state !in spanishProvinceCodes) return "Selecciona una provincia."
        return null
    }
}

/** One operation owner shared by Android and iOS profile forms. */
class StoreProfileOwner(
    private val repository: AccountRepository,
    private val sessionState: StoreAccountSessionState
) {
    private val actionMutex = Mutex()

    private fun acquire(): Boolean = actionMutex.tryLock()
    private fun release() = actionMutex.unlock()

    suspend fun save(user: AccountUser, firstName: String, lastName: String): AccountUser? {
        if (!acquire()) return null
        try {
            val first = firstName.trim()
            val last = lastName.trim()
            require(StoreAccountFormValidation.profileError(first, last) == null) {
                "Introduce tu nombre y apellidos."
            }
            // Preserve every address field not edited by Profile; this remains the existing save contract.
            val address = repository.customerAddress()
            repository.saveCustomerAddress(StoreAccountFormValidation.mergeProfileNames(address, first, last))
            val updated = user.copy(
                firstName = first,
                lastName = last,
                displayName = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user.displayName }
            )
            sessionState.publishAuthenticatedUser(updated)
            return updated
        } finally {
            release()
        }
    }
}

data class StoreAddressSnapshot(
    val address: AccountCustomerAddress = AccountCustomerAddress(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val loaded: Boolean = false
)

/** Stable address owner: cached state prevents remounts from showing a stale local copy. */
class StoreAddressOwner(private val repository: AccountRepository) {
    private val mutableSnapshot = MutableStateFlow(StoreAddressSnapshot())
    val snapshot: StateFlow<StoreAddressSnapshot> = mutableSnapshot.asStateFlow()
    private var loadStarted = false
    private val actionMutex = Mutex()
    private val loadMutex = Mutex()

    private fun acquire(): Boolean = actionMutex.tryLock()
    private fun release() = actionMutex.unlock()

    suspend fun load(force: Boolean = false) {
        if (!loadMutex.tryLock()) return
        try {
            if (loadStarted && !force) return
            loadStarted = true
        } finally {
            loadMutex.unlock()
        }
        mutableSnapshot.value = mutableSnapshot.value.copy(loading = true, error = null)
        try {
            val address = repository.customerAddress()
            mutableSnapshot.value = StoreAddressSnapshot(address = address, loading = false, loaded = true)
        } catch (cancelled: CancellationException) {
            // A screen leaving composition is normal cancellation, not a user-facing load failure.
            // Clear the started flag so a later remount can perform a fresh load.
            loadMutex.lock()
            try {
                loadStarted = false
            } finally {
                loadMutex.unlock()
            }
            throw cancelled
        } catch (failure: Exception) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                loading = false, loaded = true,
                error = failure.message ?: "No se ha podido cargar la dirección."
            )
            if (!repository.hasSession) throw failure
        }
    }

    fun edit(address: AccountCustomerAddress) {
        mutableSnapshot.value = mutableSnapshot.value.copy(address = address, error = null, notice = null)
    }

    suspend fun save(): Boolean {
        if (!acquire()) return false
        try {
            val clean = StoreAccountFormValidation.cleanAddress(mutableSnapshot.value.address)
            val validation = StoreAccountFormValidation.addressError(clean)
            if (validation != null) {
                mutableSnapshot.value = mutableSnapshot.value.copy(error = validation, notice = null)
                return false
            }
            mutableSnapshot.value = mutableSnapshot.value.copy(saving = true, error = null, notice = null)
            repository.saveCustomerAddress(clean)
            mutableSnapshot.value = mutableSnapshot.value.copy(
                address = clean, saving = false, loaded = true, notice = "Dirección actualizada", error = null
            )
            return true
        } catch (cancelled: CancellationException) {
            mutableSnapshot.value = mutableSnapshot.value.copy(saving = false, error = null, notice = null)
            throw cancelled
        } catch (failure: Exception) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                saving = false, error = failure.message ?: "No se ha podido guardar la dirección.", notice = null
            )
            if (!repository.hasSession) throw failure
            return false
        } finally {
            release()
        }
    }
}

@Composable
fun StoreProfileScreen(
    repository: AccountRepository,
    sessionState: StoreAccountSessionState,
    user: AccountUser?,
    onUserChanged: (AccountUser) -> Unit,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    val owner = remember(repository, sessionState) { StoreProfileOwner(repository, sessionState) }
    val scope = rememberCoroutineScope()
    var firstName by remember(user?.id) { mutableStateOf(user?.firstName.orEmpty()) }
    var lastName by remember(user?.id) { mutableStateOf(user?.lastName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Top) {
        StoreAccountSectionHeader("Datos personales", onBack)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(firstName, { firstName = it; error = null }, label = { Text("Nombre") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(lastName, { lastName = it; error = null }, label = { Text("Apellidos") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(user?.email.orEmpty().ifBlank { "—" }, {}, label = { Text("Correo electrónico") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(formatBirthDateForDisplay(user?.birthDate).ifBlank { "No indicada" }, {}, label = { Text("Fecha de nacimiento") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35), contentColor = Color.White),
            onClick = {
                if (busy) return@Button
                val validation = StoreAccountFormValidation.profileError(firstName, lastName)
                if (validation != null) { error = validation; notice = null; return@Button }
                busy = true; error = null; notice = null
                scope.launch {
                    try {
                        val saved = owner.save(requireNotNull(user) { "No hay una sesión de cuenta activa." }, firstName, lastName)
                        if (saved != null) { onUserChanged(saved); notice = "Datos actualizados" }
                    } catch (failure: Exception) {
                        if (!repository.hasSession) onSessionExpired()
                        else error = failure.message ?: "No se han podido guardar los datos."
                    } finally { busy = false }
                }
            }
        ) { if (busy) CircularProgressIndicator() else Text("Guardar cambios") }
    }
}

@Composable
fun StoreAddressScreen(
    repository: AccountRepository,
    addressOwner: StoreAddressOwner,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    val snapshot by addressOwner.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(addressOwner) {
        try {
            addressOwner.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!repository.hasSession) onSessionExpired()
        }
    }
    var provinceExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Top) {
        StoreAccountSectionHeader("Dirección de entrega", onBack)
        if (snapshot.loading && !snapshot.loaded) {
            CircularProgressIndicator()
        } else {
            val a = snapshot.address
            @Composable fun field(label: String, value: String, change: (String) -> Unit) {
                OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = !snapshot.saving, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            field("Nombre", a.firstName) { addressOwner.edit(a.copy(firstName = it)) }
            field("Apellidos", a.lastName) { addressOwner.edit(a.copy(lastName = it)) }
            field("Dirección", a.address1) { addressOwner.edit(a.copy(address1 = it)) }
            field("Dirección adicional / Piso, puerta, etc. (opcional)", a.address2) { addressOwner.edit(a.copy(address2 = it)) }
            field("Código postal", a.postcode) { addressOwner.edit(a.copy(postcode = it)) }
            field("Localidad", a.city) { addressOwner.edit(a.copy(city = it)) }
            Box(Modifier.fillMaxWidth()) {
                val provinceLabel = StoreAccountFormValidation.provinceOptions.firstOrNull { it.first == a.state.uppercase() }?.second ?: a.state
                OutlinedTextField(
                    provinceLabel, {}, label = { Text("Provincia") }, readOnly = true,
                    enabled = !snapshot.saving,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !snapshot.saving) { provinceExpanded = true }
                )
                DropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) {
                    StoreAccountFormValidation.provinceOptions.forEach { (code, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            addressOwner.edit(a.copy(state = code))
                            provinceExpanded = false
                        })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                "España", {}, label = { Text("País") }, readOnly = true,
                enabled = !snapshot.saving, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            snapshot.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            snapshot.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !snapshot.saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35), contentColor = Color.White),
                onClick = {
                    if (snapshot.saving) return@Button
                    scope.launch {
                        try {
                            addressOwner.save()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (!repository.hasSession) onSessionExpired()
                        }
                    }
                }
            ) { if (snapshot.saving) CircularProgressIndicator() else Text("Guardar dirección") }
        }
    }
}


/** Shared equivalent of the Android AccountSectionHeader golden. */
@Composable
fun StoreAccountSectionHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF183B35)
        )
    }
}
