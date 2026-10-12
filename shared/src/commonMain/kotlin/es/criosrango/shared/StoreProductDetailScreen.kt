package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StoreProductDetailScreen(
    state: StoreProductDetailUiState,
    modifier: Modifier = Modifier,
    topBarSlot: (@Composable () -> Unit)? = null,
    imageSlot: @Composable (Modifier) -> Unit,
    productActionsSlot: (@Composable () -> Unit)? = null,
    statusSlot: (@Composable () -> Unit)? = null,
    onBack: () -> Unit,
    onAttributeOptionSelected: (String, String) -> Unit,
    onQuantityMinus: () -> Unit,
    onQuantityPlus: () -> Unit,
    onAddToCart: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                topBarSlot?.invoke()
                StoreCatalogHeader(title = state.title, showBack = true, onBack = onBack)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
                Button(
                    onClick = onAddToCart,
                    enabled = state.primaryActionEnabled,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.primaryActionLabel == "Añadido al carrito") Color(0xFF183B35) else Color(0xFFE4EFEA),
                        contentColor = if (state.primaryActionLabel == "Añadido al carrito") Color.White else Color(0xFF183B35),
                        disabledContainerColor = Color(0xFFE3DEE3),
                        disabledContentColor = Color(0xFF9E989E)
                    )
                ) {
                    Icon(
                        imageVector = if (state.primaryActionLabel == "Añadido al carrito") Icons.Default.Check else Icons.Outlined.ShoppingBag,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.primaryActionLabel)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            imageSlot(Modifier.fillMaxWidth())
            productActionsSlot?.invoke()
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp)) {
                if (state.isOnSale) {
                    Text("OFERTA", color = Color(0xFFD18162), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF183B35)
                )
                Spacer(Modifier.height(8.dp))
                if (state.displayPrice.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.displayPrice, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF252225))
                        if (state.showRegularPrice && state.regularPrice.isNotBlank()) {
                            Text(state.regularPrice, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9E9E9E), textDecoration = TextDecoration.LineThrough)
                        }
                    }
                }
                if (state.brandText != null || state.compositionText != null || state.descriptionText.isNotBlank()) Spacer(Modifier.height(18.dp))
                state.brandText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF454145))
                }
                state.compositionText?.let {
                    if (state.brandText != null) Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF777277))
                }
                if (state.descriptionText.isNotBlank()) {
                    if (state.brandText != null || state.compositionText != null) Spacer(Modifier.height(8.dp))
                    Text(state.descriptionText, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF777277))
                }
                Spacer(Modifier.height(12.dp))
            }
            state.attributeGroups.forEach { group ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(group.label, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        group.options.forEach { option ->
                            val round = option.kind == StoreProductDetailOptionKind.Color
                            OutlinedButton(
                                onClick = { onAttributeOptionSelected(group.label, option.value) },
                                enabled = option.enabled,
                                shape = RoundedCornerShape(if (round) 50.dp else 14.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (option.selected) 2.dp else 1.dp,
                                    when {
                                        option.selected -> Color(0xFF183B35)
                                        option.incompatible -> Color(0xFFD0CDD0)
                                        else -> Color(0xFF8B878B)
                                    }
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = when {
                                        option.selected && option.kind == StoreProductDetailOptionKind.Size -> Color(0xFF183B35)
                                        option.selected -> Color(0xFFE4EFEA)
                                        else -> Color.Transparent
                                    },
                                    contentColor = when {
                                        option.selected && option.kind == StoreProductDetailOptionKind.Size -> Color.White
                                        option.incompatible -> Color(0xFF777277)
                                        else -> Color(0xFF353235)
                                    },
                                    disabledContentColor = Color(0xFFAAA6AA)
                                ),
                                contentPadding = when (option.kind) {\n                                    StoreProductDetailOptionKind.Size -> PaddingValues(horizontal = 18.dp, vertical = 10.dp)\n                                    StoreProductDetailOptionKind.Color -> PaddingValues(horizontal = 14.dp, vertical = 9.dp)\n                                    StoreProductDetailOptionKind.Default -> ButtonDefaults.ContentPadding\n                                },
                                modifier = Modifier.storeProductDetailOptionSlash(option.incompatible && !option.selected, RoundedCornerShape(if (round) 50.dp else 14.dp))
                            ) {
                                if (option.swatchColor != null) {
                                    Box(Modifier.size(20.dp).graphicsLayer { alpha = if (option.incompatible) 0.55f else 1f }.background(option.swatchColor, RoundedCornerShape(50)))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    option.value,
                                    textDecoration = if (option.strikeThrough) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            statusSlot?.invoke()
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cantidad", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = onQuantityMinus, enabled = state.quantityMinusEnabled) { Icon(Icons.Default.Remove, contentDescription = "Reducir") }
                    Text(state.quantity.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = onQuantityPlus, enabled = state.quantityPlusEnabled) { Icon(Icons.Default.Add, contentDescription = "Aumentar") }
                }
            }
        }
    }
}

private fun Modifier.storeProductDetailOptionSlash(show: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (!show) this else clip(shape).drawWithContent {
        drawContent()
        drawLine(
            color = Color(0xFF8B878B),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 1.25.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
