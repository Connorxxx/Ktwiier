package com.connor.kwitter.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path as AndroidPath

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
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
    val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)

    // Clip to circle
    val path = AndroidPath().apply {
        addCircle(
            outputSize / 2f,
            outputSize / 2f,
            outputSize / 2f,
            AndroidPath.Direction.CW
        )
    }
    canvas.clipPath(path)

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
    val imgW = sourceBitmap.width.toFloat()
    val imgH = sourceBitmap.height.toFloat()
    val fitScale = min(viewWidth / imgW, viewHeight / imgH)
    canvas.translate((viewWidth - imgW * fitScale) / 2f, (viewHeight - imgH * fitScale) / 2f)
    canvas.scale(fitScale, fitScale)
    canvas.drawBitmap(sourceBitmap, 0f, 0f, null)

    // Encode to JPEG
    val baos = ByteArrayOutputStream()
    output.compress(Bitmap.CompressFormat.JPEG, 90, baos)

    sourceBitmap.recycle()
    output.recycle()

    return baos.toByteArray()
}
