package com.connor.kwitter.features.mediaviewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.connor.kwitter.core.ui.FullscreenVideoPlayer
import com.connor.kwitter.domain.post.model.PostMediaType

private const val BASE_URL = "http://192.168.0.101:8080"

private fun resolveMediaUrl(url: String): String {
    return if (url.startsWith("http")) url else "$BASE_URL$url"
}

@Composable
fun MediaViewerScreen(
    state: MediaViewerUiState,
    onAction: (MediaViewerIntent) -> Unit
) {
    if (state.mediaList.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = state.currentIndex,
        pageCount = { state.mediaList.size }
    )

    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onAction(MediaViewerAction.PageChanged(page))
                isZoomed = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val media = state.mediaList[page]
            val mediaUrl = resolveMediaUrl(media.url)

            when (media.type) {
                PostMediaType.IMAGE -> {
                    ZoomableImageViewer(
                        imageUrl = mediaUrl,
                        onTap = { onAction(MediaViewerAction.ToggleControls) },
                        onZoomChanged = { zoomed -> isZoomed = zoomed },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                PostMediaType.VIDEO -> {
                    val isCurrentPage = page == state.currentIndex
                    Box(modifier = Modifier.fillMaxSize()) {
                        FullscreenVideoPlayer(
                            url = mediaUrl,
                            isPlaying = isCurrentPage && state.isPlaying,
                            seekPositionMs = null,
                            onPlayingChanged = { playing ->
                                if (isCurrentPage) {
                                    onAction(MediaViewerAction.PlayingChanged(playing))
                                }
                            },
                            onProgressChanged = { position, duration ->
                                if (isCurrentPage) {
                                    onAction(MediaViewerAction.UpdateProgress(position, duration))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isCurrentPage) {
                            VideoControlsOverlay(
                                isPlaying = state.isPlaying,
                                currentPositionMs = state.currentPositionMs,
                                durationMs = state.durationMs,
                                showControls = state.showControls,
                                onTogglePlayPause = { onAction(MediaViewerAction.TogglePlayPause) },
                                onSeekTo = { pos -> onAction(MediaViewerAction.SeekTo(pos)) },
                                onToggleControls = { onAction(MediaViewerAction.ToggleControls) },
                                onAutoHide = { onAction(MediaViewerAction.ToggleControls) }
                            )
                        }
                    }
                }
            }
        }

        // Close button (top-left)
        Box(
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .size(36.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onAction(MediaViewerNavAction.BackClick) }
                )
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                modifier = Modifier.size(16.dp),
                color = Color.White
            )
        }

        // Page indicator (top-right)
        if (state.mediaList.size > 1) {
            Box(
                modifier = Modifier
                    .padding(top = 52.dp, end = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    text = "${state.currentIndex + 1}/${state.mediaList.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CloseIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val margin = size.minDimension * 0.15f
        // X shape: top-left to bottom-right
        drawLine(
            color = color,
            start = Offset(margin, margin),
            end = Offset(size.width - margin, size.height - margin),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // top-right to bottom-left
        drawLine(
            color = color,
            start = Offset(size.width - margin, margin),
            end = Offset(margin, size.height - margin),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
