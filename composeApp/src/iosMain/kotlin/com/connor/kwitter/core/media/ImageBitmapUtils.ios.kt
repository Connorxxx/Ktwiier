package com.connor.kwitter.core.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.math.min
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Path as SkiaPath

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap {
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}

actual fun cropCircle(
    sourceBytes: ByteArray,
    viewWidth: Int,
    viewHeight: Int,
    circleRadius: Float,
    scale: Float,
    offset: Offset,
    outputSize: Int
): ByteArray {
    val skiaImage = SkiaImage.makeFromEncoded(sourceBytes)
    val imgW = skiaImage.width.toFloat()
    val imgH = skiaImage.height.toFloat()

    val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
    val canvas = surface.canvas

    // Clip output to circle
    val circlePath = SkiaPath().apply {
        addCircle(outputSize / 2f, outputSize / 2f, outputSize / 2f)
    }
    canvas.clipPath(circlePath)

    // --- Transform chain: output canvas → view space → image ---

    // 1. Map output canvas to the circle region in view space
    val viewCenterX = viewWidth / 2f
    val viewCenterY = viewHeight / 2f
    val canvasToView = (2f * circleRadius) / outputSize
    canvas.scale(1f / canvasToView, 1f / canvasToView)
    canvas.translate(-(viewCenterX - circleRadius), -(viewCenterY - circleRadius))

    // 2. Replicate graphicsLayer: scale around view center + translate
    canvas.translate(viewCenterX + offset.x, viewCenterY + offset.y)
    canvas.scale(scale, scale)
    canvas.translate(-viewCenterX, -viewCenterY)

    // 3. Draw image at its ContentScale.Fit position
    val fitScale = min(viewWidth / imgW, viewHeight / imgH)
    canvas.translate((viewWidth - imgW * fitScale) / 2f, (viewHeight - imgH * fitScale) / 2f)
    canvas.scale(fitScale, fitScale)
    canvas.drawImage(skiaImage, 0f, 0f)

    // Encode to JPEG
    val snapshot = surface.makeImageSnapshot()
    val data = snapshot.encodeToData(EncodedImageFormat.JPEG, 90)
        ?: error("Failed to encode cropped image")
    val bytes = data.bytes

    circlePath.close()
    snapshot.close()
    surface.close()
    skiaImage.close()

    return bytes
}
