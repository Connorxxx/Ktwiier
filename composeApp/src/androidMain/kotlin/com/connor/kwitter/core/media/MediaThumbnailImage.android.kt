package com.connor.kwitter.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ComponentRegistry
import coil3.compose.AsyncImage
import coil3.video.VideoFrameDecoder

@Composable
actual fun MediaThumbnailImage(
    media: SelectedMedia,
    modifier: Modifier,
    contentScale: ContentScale
) {
    AsyncImage(
        model = media.platformData,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    )
}

internal actual fun ComponentRegistry.Builder.addPlatformComponents() {
    add(VideoFrameDecoder.Factory())
}
