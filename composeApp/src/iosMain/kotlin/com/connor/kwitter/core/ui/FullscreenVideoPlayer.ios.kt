package com.connor.kwitter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import com.connor.kwitter.core.media.VideoCache
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FullscreenVideoPlayer(
    url: String,
    isPlaying: Boolean,
    seekPositionMs: Long?,
    onPlayingChanged: (Boolean) -> Unit,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit,
    modifier: Modifier
) {
    val playUrl = remember(url) {
        VideoCache.cachedFileUrl(url) ?: NSURL.URLWithString(url)
    } ?: return
    val player = remember(url) {
        if (VideoCache.cachedFileUrl(url) == null) {
            VideoCache.cacheVideoAsync(url)
        }
        AVPlayer.playerWithURL(playUrl).apply {
            muted = false
        }
    }

    // Set audio category + end-of-playback notification
    DisposableEffect(player) {
        AVAudioSession.sharedInstance().setCategory(
            category = AVAudioSessionCategoryPlayback,
            error = null
        )

        val endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = null
        ) { _ ->
            player.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
            player.play()
        }

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(endObserver)
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    // Sync play/pause from state
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Handle seek
    LaunchedEffect(seekPositionMs) {
        if (seekPositionMs != null) {
            val cmTime = CMTimeMakeWithSeconds(seekPositionMs / 1000.0, 600)
            player.seekToTime(cmTime)
        }
    }

    // Poll progress only (no play state feedback to avoid race with buffering)
    LaunchedEffect(player) {
        while (true) {
            delay(200L)
            val currentSeconds = CMTimeGetSeconds(player.currentTime())
            val durationSeconds = player.currentItem?.let { CMTimeGetSeconds(it.duration) } ?: 0.0
            val posMs = if (currentSeconds.isNaN() || currentSeconds < 0) 0L else (currentSeconds * 1000).toLong()
            val durMs = if (durationSeconds.isNaN() || durationSeconds < 0) 0L else (durationSeconds * 1000).toLong()
            onProgressChanged(posMs, durMs)
        }
    }

    UIKitViewController(
        modifier = modifier,
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = false
                videoGravity = AVLayerVideoGravityResizeAspect
            }
        },
        properties = UIKitInteropProperties(interactionMode = null)
    )
}
