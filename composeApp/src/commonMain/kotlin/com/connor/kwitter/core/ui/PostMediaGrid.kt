package com.connor.kwitter.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostMediaType

private const val BASE_URL = "http://192.168.0.101:8080"

@Composable
fun PostMediaGrid(
    media: List<PostMedia>,
    modifier: Modifier = Modifier
) {
    if (media.isEmpty()) return

    val shape = RoundedCornerShape(12.dp)

    when (media.size) {
        1 -> {
            PostMediaItem(
                media = media[0],
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        2 -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                media.forEach { item ->
                    PostMediaItem(
                        media = item,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp, max = 200.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
        }
        3 -> {
            Row(
                modifier = modifier.fillMaxWidth().heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PostMediaItem(
                    media = media[0],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PostMediaItem(
                        media = media[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    PostMediaItem(
                        media = media[2],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
        }
        else -> {
            // Up to 4 items: 2x2 grid
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0..1) {
                        PostMediaItem(
                            media = media[i],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.5f)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 2..minOf(3, media.lastIndex)) {
                        PostMediaItem(
                            media = media[i],
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.5f)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostMediaItem(
    media: PostMedia,
    modifier: Modifier = Modifier
) {
    val mediaUrl = resolveMediaUrl(media.url)
    when (media.type) {
        PostMediaType.IMAGE -> AsyncImage(
            model = mediaUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )

        PostMediaType.VIDEO -> AutoPlayVideoPlayer(
            url = mediaUrl,
            modifier = modifier
        )
    }
}

private fun resolveMediaUrl(url: String): String {
    return if (url.startsWith("http")) url else "$BASE_URL$url"
}
