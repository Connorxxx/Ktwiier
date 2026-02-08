package com.connor.kwitter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FullscreenVideoPlayer(
    url: String,
    isPlaying: Boolean,
    seekPositionMs: Long?,
    onPlayingChanged: (Boolean) -> Unit,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier
)
