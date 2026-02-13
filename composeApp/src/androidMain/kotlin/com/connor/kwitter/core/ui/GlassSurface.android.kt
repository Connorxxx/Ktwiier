package com.connor.kwitter.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

@Composable
actual fun GlassSurface(
    modifier: Modifier,
    shape: Shape,
    content: @Composable () -> Unit
) {
    // TODO: Investigate RenderEffect or other blur approach for Android glass effect.
    //  Current limitation: RenderEffect cannot blur AndroidView (ExoPlayer SurfaceView).
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
    ) {
        content()
    }
}
