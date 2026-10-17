package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
