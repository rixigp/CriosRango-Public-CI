package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.splash_logo
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun StoreStartupBranding() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAF7F0)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
    }
}
