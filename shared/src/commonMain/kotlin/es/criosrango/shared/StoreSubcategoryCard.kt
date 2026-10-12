package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.scale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.criosrango.shared.model.StoreCategory
import org.jetbrains.compose.resources.painterResource

/** Shared subcategory cell matching Android ApprovedCategoryCell geometry and typography. */
@Composable
fun StoreSubcategoryCard(
    parent: StoreCategory,
    category: StoreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imagePadding: Dp = 1.dp,
    imageScale: Float = 1.16f,
    imageToLabelSpacing: Dp = 6.dp
) {
    val image = storeSubcategoryResource(parent.slug, category.slug)
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(painter = painterResource(image), contentDescription = category.name,
                    modifier = Modifier.fillMaxSize().padding(imagePadding).scale(imageScale), contentScale = ContentScale.Fit)
            } else {
                Text(category.name.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.height(imageToLabelSpacing))
        Text(category.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
