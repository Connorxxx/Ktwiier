package com.connor.kwitter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
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
    modifier: Modifier
) {
    val nsUrl = remember(url) { NSURL.URLWithString(url) } ?: return
    val player = remember(url) {
        AVPlayer.playerWithURL(nsUrl).apply {
            muted = true
            volume = 0f
        }
    }

    DisposableEffect(player) {
        AVAudioSession.sharedInstance().setCategory(
            category = AVAudioSessionCategoryAmbient,
            withOptions = AVAudioSessionCategoryOptionMixWithOthers,
            error = null
        )

        player.play()

        val loopObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = null
        ) { _ ->
            player.seekToTime(CMTimeMake(value = 0L, timescale = 1))
            player.play()
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
