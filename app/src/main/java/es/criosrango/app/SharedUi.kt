package es.criosrango.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.ui.draw.scale

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import android.view.TextureView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.outlined.*
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import kotlinx.coroutines.launch

@Composable
internal fun CategoryVisual(category: ProductCategory, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(categoryIcon(category.name), category.name, tint = Color(0xFF183B35), modifier = Modifier.size(30.dp))
    }
}

internal fun categoryIcon(name: String): ImageVector {
    val category = name.normalizedKey()
    val explicit = mapOf(
        "hombre" to FashionIcons.Polo,
        "mujer" to FashionIcons.Dress,
        "nina" to FashionIcons.ChildDress,
        "nino" to FashionIcons.Shirt,
        "bebe nina" to FashionIcons.BabyGirl,
        "bebe nino" to FashionIcons.BabyBody,
        "recien nacido" to FashionIcons.Crib,
        "calzado" to FashionIcons.Shoe,
        "bautizo" to FashionIcons.Ceremony,
        "comunion nina" to FashionIcons.CommunionDress,
        "comunion nino" to FashionIcons.Suit,
        "outlet" to TablerIcons.Tag,
        "abrigos y cazadoras" to FashionIcons.Coat,
        "americanas y trajes" to FashionIcons.Suit,
        "vestidos" to FashionIcons.Dress,
        "camisas" to TablerIcons.Shirt,
        "camisetas" to TablerIcons.Shirt,
        "pantalones" to FashionIcons.Pants,
        "faldas" to FashionIcons.Skirt,
        "pijamas" to TablerIcons.Bed,
        "bodys" to FashionIcons.BabyBody,
        "conjuntos y monos" to FashionIcons.Outfit,
        "complementos" to TablerIcons.Hanger,
        "bano" to Icons.Default.Bathtub,
        "complementos y bano" to Icons.Default.Bathtub
    )
    explicit[category]?.let { return it }
    return when {
        "recien nacido" in category -> FashionIcons.Crib
        "bebe" in category || "body" in category -> FashionIcons.BabyBody
        "calzad" in category -> FashionIcons.Shoe
        "bautiz" in category -> FashionIcons.Ceremony
        "comunion" in category && "nina" in category -> FashionIcons.CommunionDress
        "comunion" in category && "nino" in category -> FashionIcons.Suit
        "complement" in category && "bano" in category -> Icons.Default.Bathtub
        "complement" in category || "accesorio" in category -> FashionIcons.Bag
        "abrigo" in category || "cazador" in category -> FashionIcons.Coat
        "american" in category || "traje" in category -> FashionIcons.Suit
        "conjunto" in category || "mono" in category -> FashionIcons.Outfit
        "vestido" in category -> FashionIcons.Dress
        "falda" in category -> FashionIcons.Skirt
        "camisa" in category || "camiseta" in category -> FashionIcons.Shirt
        "pantalon" in category -> FashionIcons.Pants
        "pijama" in category || "dormir" in category -> Icons.Default.Bedtime
        "outlet" in category || "oferta" in category -> Icons.Outlined.LocalOffer
        "hombre" in category -> FashionIcons.Polo
        "mujer" in category -> FashionIcons.Dress
        "nina" in category -> FashionIcons.ChildDress
        "nino" in category -> FashionIcons.Shirt
        else -> FashionIcons.Outfit
    }
}

internal object FashionIcons {
    internal fun lineIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathBuilder().apply(draw).nodes, fill = null, stroke = SolidColor(Color(0xFF183B35)), strokeLineWidth = 1.7f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
        .build()

    val Shirt by lazy { lineIcon("Shirt") { moveTo(8f, 4f); lineTo(4f, 7f); lineTo(2.8f, 12f); lineTo(6f, 13.5f); lineTo(7f, 10f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(17f, 10f); lineTo(18f, 13.5f); lineTo(21.2f, 12f); lineTo(20f, 7f); lineTo(16f, 4f); lineTo(14f, 6f); lineTo(10f, 6f); close() } }
    val Polo by lazy { lineIcon("Polo") { moveTo(8f, 4f); lineTo(11f, 7f); lineTo(13f, 7f); lineTo(16f, 4f); lineTo(20f, 7f); lineTo(18f, 12f); lineTo(16f, 10f); lineTo(16f, 20f); lineTo(8f, 20f); lineTo(8f, 10f); lineTo(6f, 12f); lineTo(4f, 7f); close(); moveTo(11f, 7f); lineTo(12f, 10f); lineTo(13f, 7f) } }
    val Dress by lazy { lineIcon("Dress") { moveTo(9f, 4f); lineTo(15f, 4f); lineTo(14f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(10f, 9f); close() } }
    val ChildDress by lazy { lineIcon("ChildDress") { moveTo(8f, 5f); lineTo(10f, 7f); lineTo(14f, 7f); lineTo(16f, 5f); lineTo(18f, 8f); lineTo(15f, 10f); lineTo(19f, 20f); lineTo(5f, 20f); lineTo(9f, 10f); lineTo(6f, 8f); close(); moveTo(9f, 12f); lineTo(15f, 12f) } }
    val BabyBody by lazy { lineIcon("BabyBody") { moveTo(8f, 4f); lineTo(10f, 6f); lineTo(14f, 6f); lineTo(16f, 4f); lineTo(19f, 7f); lineTo(17f, 10f); lineTo(17f, 16f); lineTo(15f, 20f); lineTo(12f, 17f); lineTo(9f, 20f); lineTo(7f, 16f); lineTo(7f, 10f); lineTo(5f, 7f); close() } }
    val BabyGirl by lazy { lineIcon("BabyGirl") { moveTo(8f, 4f); lineTo(10f, 6f); lineTo(14f, 6f); lineTo(16f, 4f); lineTo(19f, 7f); lineTo(17f, 10f); lineTo(17f, 15f); lineTo(19f, 19f); lineTo(15f, 19f); lineTo(12f, 16f); lineTo(9f, 19f); lineTo(5f, 19f); lineTo(7f, 15f); lineTo(7f, 10f); lineTo(5f, 7f); close() } }
    val Crib by lazy { lineIcon("Crib") { moveTo(3f, 8f); lineTo(21f, 8f); lineTo(20f, 18f); lineTo(4f, 18f); close(); moveTo(7f, 8f); lineTo(7f, 18f); moveTo(11f, 8f); lineTo(11f, 18f); moveTo(15f, 8f); lineTo(15f, 18f); moveTo(19f, 8f); lineTo(19f, 18f); moveTo(3f, 21f); lineTo(7f, 18f); moveTo(21f, 21f); lineTo(17f, 18f) } }
    val Shoe by lazy { lineIcon("Shoe") { moveTo(3f, 15f); lineTo(8f, 14f); lineTo(12f, 9f); lineTo(15f, 13f); lineTo(20f, 15f); lineTo(21f, 19f); lineTo(3f, 19f); close(); moveTo(12f, 9f); lineTo(9f, 6f) } }
    val Ceremony by lazy { lineIcon("Ceremony") { moveTo(9f, 4f); lineTo(15f, 4f); lineTo(14f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(10f, 9f); close(); moveTo(12f, 2f); lineTo(12f, 6f); moveTo(10f, 4f); lineTo(14f, 4f) } }
    val CommunionDress by lazy { lineIcon("CommunionDress") { moveTo(9f, 4f); lineTo(15f, 4f); lineTo(14f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(10f, 9f); close(); moveTo(8f, 7f); lineTo(16f, 7f); moveTo(7f, 12f); lineTo(17f, 12f) } }
    val Suit by lazy { lineIcon("Suit") { moveTo(8f, 4f); lineTo(12f, 7f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(17f, 11f); lineTo(17f, 20f); lineTo(7f, 20f); lineTo(7f, 11f); lineTo(4f, 8f); close(); moveTo(12f, 7f); lineTo(12f, 20f) } }
    val Coat by lazy { lineIcon("Coat") { moveTo(8f, 4f); lineTo(12f, 7f); lineTo(16f, 4f); lineTo(19f, 7f); lineTo(17f, 20f); lineTo(7f, 20f); lineTo(5f, 7f); close(); moveTo(12f, 7f); lineTo(12f, 20f) } }
    val Pants by lazy { lineIcon("Pants") { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(18f, 20f); lineTo(13f, 20f); lineTo(12f, 13f); lineTo(11f, 20f); lineTo(6f, 20f); close() } }
    val Skirt by lazy { lineIcon("Skirt") { moveTo(9f, 4f); lineTo(15f, 4f); lineTo(14f, 8f); lineTo(19f, 20f); lineTo(5f, 20f); lineTo(10f, 8f); close() } }
    val Outfit by lazy { lineIcon("Outfit") { moveTo(4f, 5f); lineTo(10f, 5f); lineTo(10f, 18f); lineTo(4f, 18f); close(); moveTo(14f, 5f); lineTo(20f, 5f); lineTo(20f, 18f); lineTo(14f, 18f); close(); moveTo(6f, 8f); lineTo(8f, 8f); moveTo(16f, 8f); lineTo(18f, 8f) } }
    val Bag by lazy { lineIcon("Bag") { moveTo(4f, 8f); lineTo(20f, 8f); lineTo(19f, 20f); lineTo(5f, 20f); close(); moveTo(8f, 8f); lineTo(8f, 6f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(16f, 6f); lineTo(16f, 8f) } }
}

@Composable internal fun CatalogImage(url: String?, description: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, onSuccess: (() -> Unit)? = null) { if (url.isNullOrBlank()) Box(modifier.background(Color(0xFFE8E5DF)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, tint = Color.Gray) } else AsyncImage(model = url, contentDescription = description, modifier = modifier.background(Color(0xFFE8E5DF)), contentScale = contentScale, onSuccess = { onSuccess?.invoke() }) }
