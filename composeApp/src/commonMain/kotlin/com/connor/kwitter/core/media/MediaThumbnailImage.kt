package com.connor.kwitter.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ComponentRegistry

@Composable
expect fun MediaThumbnailImage(
    media: SelectedMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
)

internal expect fun ComponentRegistry.Builder.addPlatformComponents()
