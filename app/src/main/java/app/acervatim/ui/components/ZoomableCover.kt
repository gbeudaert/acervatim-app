package app.acervatim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.acervatim.R
import java.io.File

/**
 * Jaquette d'affichage partagée : rend exactement comme [RecordCover], mais lorsqu'une image existe,
 * un tap ouvre une vue plein écran zoomable (pinch + pan). Réutilisée par tous les écrans de détail
 * (vinyle, manga, série) pour garantir un comportement uniforme — pas de copie par écran.
 *
 * Sans jaquette (placeholder), le composant n'est pas cliquable : rien à agrandir.
 */
@Composable
fun ZoomableCover(
    coverPath: String?,
    contentDescription: String?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    cacheKey: String? = null,
    placeholderIconSize: Dp = 24.dp
) {
    val file = coverPath?.let(::File)?.takeIf { it.exists() }
    var zoomed by remember { mutableStateOf(false) }
    val zoomLabel = stringResource(R.string.cover_zoom_open)

    RecordCover(
        coverPath = coverPath,
        contentDescription = contentDescription,
        cornerRadius = cornerRadius,
        cacheKey = cacheKey,
        placeholderIconSize = placeholderIconSize,
        modifier = if (file != null) {
            modifier.clickable(onClickLabel = zoomLabel) { zoomed = true }
        } else {
            modifier
        }
    )

    if (zoomed && file != null) {
        FullScreenCoverDialog(
            file = file,
            cacheKey = cacheKey,
            contentDescription = contentDescription,
            onDismiss = { zoomed = false }
        )
    }
}

@Composable
private fun FullScreenCoverDialog(
    file: File,
    cacheKey: String?,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            // Pan only meaningful once zoomed in; reset translation when back to fit.
            if (scale > 1f) {
                offsetX += panChange.x
                offsetY += panChange.y
            } else {
                offsetX = 0f
                offsetY = 0f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            val context = LocalContext.current
            val request = ImageRequest.Builder(context)
                .data(file)
                .crossfade(true)
                .apply { if (cacheKey != null) memoryCacheKey(cacheKey) }
                .build()
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .transformable(transformState)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White
                )
            }
        }
    }
}
