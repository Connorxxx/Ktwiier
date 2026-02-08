package com.connor.kwitter.core.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ComponentRegistry
import coil3.compose.AsyncImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@Composable
actual fun MediaThumbnailImage(
    media: SelectedMedia,
    modifier: Modifier,
    contentScale: ContentScale
) {
    if (media.mimeType.startsWith("video/")) {
        VideoThumbnail(media = media, modifier = modifier, contentScale = contentScale)
    } else {
        val bytes = remember(media) {
            (media.platformData as NSData).toByteArray()
        }
        AsyncImage(
            model = bytes,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
private fun VideoThumbnail(
    media: SelectedMedia,
    modifier: Modifier,
    contentScale: ContentScale
) {
    var thumbnailBitmap by remember(media) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(media) {
        withContext(Dispatchers.IO) {
            thumbnailBitmap = generateVideoThumbnail(media.platformData as NSData)
        }
    }

    if (thumbnailBitmap != null) {
        Image(
            bitmap = thumbnailBitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun generateVideoThumbnail(data: NSData): ImageBitmap? {
    val tempPath = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
    data.writeToFile(tempPath, atomically = true)

    return try {
        val url = NSURL.fileURLWithPath(tempPath)
        val asset = AVURLAsset(uRL = url, options = null)
        val generator = AVAssetImageGenerator(asset = asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSizeMake(400.0, 400.0)

        val time = CMTimeMake(value = 0, timescale = 1)
        val cgImage = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            generator.copyCGImageAtTime(requestedTime = time, actualTime = null, error = error.ptr)
        } ?: return null

        val uiImage = UIImage(cGImage = cgImage)
        val jpegData = UIImageJPEGRepresentation(uiImage, 0.7) ?: return null
        val bytes = jpegData.toByteArray()

        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(tempPath, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

internal actual fun ComponentRegistry.Builder.addPlatformComponents() {
    // No additional Coil components needed on iOS
}
