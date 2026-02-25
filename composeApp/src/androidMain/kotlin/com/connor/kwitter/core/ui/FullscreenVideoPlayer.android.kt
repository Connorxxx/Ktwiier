package com.connor.kwitter.core.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.connor.kwitter.core.media.VideoCache
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
actual fun FullscreenVideoPlayer(
    url: String,
    isPlaying: Boolean,
    seekPositionMs: Long?,
    onPlayingChanged: (Boolean) -> Unit,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(VideoCache.getCacheDataSourceFactory(context))
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
                playWhenReady = false
                prepare()
            }
    }

    // Sync isPlaying from state to player
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Handle seek
    LaunchedEffect(seekPositionMs) {
        if (seekPositionMs != null) {
            exoPlayer.seekTo(seekPositionMs)
        }
    }

    // Poll progress only (no play state feedback to avoid race with buffering)
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(200L)
            val position = exoPlayer.currentPosition.coerceAtLeast(0L)
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            onProgressChanged(position, duration)
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        }
    )
}
