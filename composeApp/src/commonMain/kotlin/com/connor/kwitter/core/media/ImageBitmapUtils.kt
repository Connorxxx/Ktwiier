package com.connor.kwitter.core.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap

/**
 * Crops the source image to a circle using the same transforms displayed in the crop composable.
 *
 * The composable renders the image with ContentScale.Fit inside a view of
 * [viewWidth] x [viewHeight], then applies a graphicsLayer with [scale] (around center)
 * and [offset] (translation). The visible circle is centered in the view with [circleRadius].
 *
 * This function replicates those transforms on a canvas clipped to a circle,
 * producing a square JPEG of [outputSize] x [outputSize] pixels.
 */
expect fun cropCircle(
    sourceBytes: ByteArray,
    viewWidth: Int,
    viewHeight: Int,
    circleRadius: Float,
    scale: Float,
    offset: Offset,
    outputSize: Int = 512
): ByteArray
