package es.criosrango.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun productColorSwatch(value: String): Color? {
    val key = java.text.Normalizer.normalize(value.lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return when {
        key.startsWith("#") && key.length == 7 -> runCatching {
            val rgb = key.substring(1).toLong(16).toInt()
            Color(0xFF000000.toInt() or rgb)
        }.getOrNull()
        "azul pavo" in key -> Color(0xFF315D66)
        "marino" in key -> Color(0xFF1F2D4D)
        "azul" in key -> Color(0xFF527DA8)
        "celeste" in key -> Color(0xFF9BCBE3)
        "turquesa" in key -> Color(0xFF3AAFA9)
        "verde botella" in key -> Color(0xFF274E3C)
        "verde" in key -> Color(0xFF668A63)
        "menta" in key -> Color(0xFFA8D5BA)
        "rojo" in key -> Color(0xFFB94A48)
        "granate" in key || "burdeos" in key -> Color(0xFF743A46)
        "rosa" in key -> Color(0xFFE6A7B8)
        "fucsia" in key -> Color(0xFFC74779)
        "naranja" in key -> Color(0xFFD9824B)
        "amarillo" in key -> Color(0xFFE4C34A)
        "mostaza" in key -> Color(0xFFC89B3C)
        "violeta" in key || "morado" in key -> Color(0xFF80658D)
        "lila" in key -> Color(0xFFB6A0C9)
        "beige" in key || "arena" in key -> Color(0xFFD8C6A5)
        "camel" in key -> Color(0xFFB9855C)
        "marron" in key || "chocolate" in key -> Color(0xFF765344)
        "crudo" in key -> Color(0xFFE9E1D1)
        "berenjena" in key -> Color(0xFF5B3A5A)
        "caldero" in key -> Color(0xFFB5653D)
        "chicle" in key -> Color(0xFFF3A6C8)
        "coral" in key -> Color(0xFFF08080)
        "kaki" in key -> Color(0xFF8A8F5A)
        "oro" in key -> Color(0xFFD4AF37)
        "pistacho" in key -> Color(0xFFA8C686)
        "salmon" in key -> Color(0xFFFA8072)
        "blanco" in key -> Color.White
        "negro" in key -> Color(0xFF222222)
        "gris" in key -> Color(0xFF8A8A8A)
        else -> null
    }
}

