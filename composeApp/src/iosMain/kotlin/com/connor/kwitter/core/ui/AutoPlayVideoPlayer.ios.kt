package com.connor.kwitter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import com.connor.kwitter.core.media.VideoCache
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AutoPlayVideoPlayer(
    url: String,
    modifier: Modifier,
    isPlaying: Boolean
) {
    val playUrl = remember(url) {
        VideoCache.cachedFileUrl(url) ?: NSURL.URLWithString(url)
    } ?: return
    val currentIsPlaying = rememberUpdatedState(isPlaying)

    val player = remember(url) {
        // Trigger background download for uncached videos
        if (VideoCache.cachedFileUrl(url) == null) {
            VideoCache.cacheVideoAsync(url)
        }
        AVPlayer.playerWithURL(playUrl).apply {
            muted = true
            volume = 0f
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }

    DisposableEffect(player) {
        AVAudioSession.sharedInstance().setCategory(
            category = AVAudioSessionCategoryAmbient,
            withOptions = AVAudioSessionCategoryOptionMixWithOthers,
            error = null
        )

        if (currentIsPlaying.value) {
            player.play()
        }

        val loopObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = null
        ) { _ ->
            if (currentIsPlaying.value) {
                player.seekToTime(CMTimeMake(value = 0L, timescale = 1))
                player.play()
            }
        }

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(loopObserver)
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    UIKitViewController(
        modifier = modifier,
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = false
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
        },
        properties = UIKitInteropProperties(interactionMode = null)
    )
}
