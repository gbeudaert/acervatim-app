package app.acervatim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

/**
 * Jaquette d'un item.
 *
 * Deux sources, dans cet ordre : le **fichier local** ([coverPath], photo prise ou image
 * téléchargée par cet appareil), puis l'**URL du provider** ([coverUrl]). L'ordre compte — une image
 * locale est celle que l'utilisateur a choisie, elle prime sur celle du catalogue.
 *
 * [coverUrl] existe parce que le fichier local est, par construction, propre à un appareil : une
 * collection reçue en partage n'en a aucun (le backend ne stocke aucune image), et un disque
 * synchronisé depuis un autre téléphone non plus. Sans ce repli, les deux affichaient un placeholder
 * alors que l'URL était disponible.
 */
@Composable
fun RecordCover(
    coverPath: String?,
    contentDescription: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    cacheKey: String? = null,
    placeholderIconSize: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val file = coverPath?.let(::File)?.takeIf { it.exists() }
        val source: Any? = file ?: coverUrl?.takeIf { it.isNotBlank() }
        if (source != null) {
            val context = LocalContext.current
            val request = ImageRequest.Builder(context)
                .data(source)
                .crossfade(true)
                // Clé mémoire seulement pour le fichier local : elle est indexée sur l'id de l'item,
                // or la même URL peut être partagée par plusieurs items (rééditions).
                .apply { if (cacheKey != null && file != null) memoryCacheKey(cacheKey) }
                .build()
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Album,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(placeholderIconSize)
            )
        }
    }
}
