package com.connor.kwitter.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import platform.posix.memcpy

private object PickerDelegateHolder {
    var activeDelegate: NSObject? = null
}

@Composable
actual fun rememberMediaPickerLauncher(
    onResult: (List<SelectedMedia>) -> Unit
): () -> Unit {
    return remember {
        {
            val config = PHPickerConfiguration().apply {
                selectionLimit = 4
                selection = PHPickerConfigurationSelectionOrdered
                filter = PHPickerFilter.anyFilterMatchingSubfilters(
                    listOf(
                        PHPickerFilter.imagesFilter,
                        PHPickerFilter.videosFilter
                    )
                )
            }

            val delegate = MultiMediaPickerDelegate(onResult)
            val picker = PHPickerViewController(configuration = config)

            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController != null) {
                PickerDelegateHolder.activeDelegate = delegate
                picker.delegate = delegate
                rootViewController.presentViewController(picker, animated = true, completion = null)
            } else {
                PickerDelegateHolder.activeDelegate = null
                onResult(emptyList())
            }
        }
    }
}

@Composable
actual fun rememberImagePickerLauncher(
    onResult: (SelectedMedia?) -> Unit
): () -> Unit {
    return remember {
        {
            val config = PHPickerConfiguration().apply {
                selectionLimit = 1
                filter = PHPickerFilter.imagesFilter
            }

            val delegate = SingleImagePickerDelegate(onResult)
            val picker = PHPickerViewController(configuration = config)

            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController != null) {
                PickerDelegateHolder.activeDelegate = delegate
                picker.delegate = delegate
                rootViewController.presentViewController(picker, animated = true, completion = null)
            } else {
                PickerDelegateHolder.activeDelegate = null
                onResult(null)
            }
        }
    }
}

private class MultiMediaPickerDelegate(
    private val onResult: (List<SelectedMedia>) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        PickerDelegateHolder.activeDelegate = null
        picker.dismissViewControllerAnimated(true, completion = null)

        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onResult(emptyList())
            return
        }

        val media = mutableListOf<SelectedMedia>()
        var remaining = results.size

        results.forEach { result ->
            val provider = result.itemProvider
            val typeIdentifier = when {
                provider.hasItemConformingToTypeIdentifier(UTTypeImage.identifier) -> UTTypeImage.identifier
                provider.hasItemConformingToTypeIdentifier(UTTypeMovie.identifier) -> UTTypeMovie.identifier
                else -> null
            }

            if (typeIdentifier == null) {
                remaining--
                if (remaining == 0) onResult(media.toList())
                return@forEach
            }

            val mimeType = when (typeIdentifier) {
                UTTypeImage.identifier -> "image/jpeg"
                UTTypeMovie.identifier -> "video/mp4"
                else -> "application/octet-stream"
            }

            @Suppress("UNCHECKED_CAST")
            provider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, _ ->
                if (data != null) {
                    media.add(
                        SelectedMedia(
                            platformData = data,
                            name = provider.suggestedName ?: "media",
                            mimeType = mimeType
                        )
                    )
                }
                remaining--
                if (remaining == 0) {
                    onResult(media.toList())
                }
            }
        }
    }
}

private class SingleImagePickerDelegate(
    private val onResult: (SelectedMedia?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        PickerDelegateHolder.activeDelegate = null
        picker.dismissViewControllerAnimated(true, completion = null)

        val result = didFinishPicking.filterIsInstance<PHPickerResult>().firstOrNull()
        if (result == null) {
            onResult(null)
            return
        }

        val provider = result.itemProvider
        if (!provider.hasItemConformingToTypeIdentifier(UTTypeImage.identifier)) {
            onResult(null)
            return
        }

        @Suppress("UNCHECKED_CAST")
        provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            if (data != null) {
                onResult(
                    SelectedMedia(
                        platformData = data,
                        name = provider.suggestedName ?: "avatar",
                        mimeType = "image/jpeg"
                    )
                )
            } else {
                onResult(null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun SelectedMedia.readBytes(): ByteArray {
    val nsData = platformData as NSData
    return nsData.toByteArray()
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
