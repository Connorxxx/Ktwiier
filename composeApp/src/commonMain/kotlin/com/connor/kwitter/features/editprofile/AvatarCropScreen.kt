package com.connor.kwitter.features.editprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connor.kwitter.core.media.cropCircle
import com.connor.kwitter.core.media.decodeToImageBitmap
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.features.glass.NativeTopBarAction
import com.connor.kwitter.features.glass.NativeTopBarButtonAction
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.rememberNativeTopBarController
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCropScreen(
    imageBytes: ByteArray,
    onConfirm: (ByteArray) -> Unit,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onCancel: () -> Unit
) {
    val imageBitmap = remember(imageBytes) { decodeToImageBitmap(imageBytes) }
    val nativeTopBarController = rememberNativeTopBarController()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(onNativeTopBarModel) {
        onNativeTopBarModel(
            NativeTopBarModel.Title(
                title = "Crop Avatar",
                leadingButton = NativeTopBarButtons.back(),
                preferLightForeground = true
            )
        )
    }

    LaunchedEffect(nativeTopBarController) {
        nativeTopBarController?.actionEvents?.collect { action ->
            if (
                action is NativeTopBarAction.ButtonClicked &&
                action.action == NativeTopBarButtonAction.Back
            ) {
                onCancel()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        NativeTopBarSlot(nativeTopBarController = nativeTopBarController) {
            CropTopBar(
                onCancel = onCancel
            )
        }

        // Crop area
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val viewWidth = constraints.maxWidth.toFloat()
            val viewHeight = constraints.maxHeight.toFloat()
            val circleRadius = min(viewWidth, viewHeight) * 0.4f

            val imgW = imageBitmap.width.toFloat()
            val imgH = imageBitmap.height.toFloat()
            val fitScale = min(viewWidth / imgW, viewHeight / imgH)
            val fittedW = imgW * fitScale
            val fittedH = imgH * fitScale
            val minScale = ((circleRadius * 2f) / min(fittedW, fittedH)).coerceAtLeast(1f)

            // Ensure scale meets minimum on first composition
            if (scale < minScale) {
                scale = minScale
            }

            // Image with gesture handling
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(minScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, 5f)
                            val newOffset = clampOffset(
                                offset = offset + pan,
                                scale = newScale,
                                fittedWidth = fittedW,
                                fittedHeight = fittedH,
                                circleRadius = circleRadius
                            )
                            scale = newScale
                            offset = newOffset
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            // Semi-transparent overlay with circular cutout
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                // Full dark overlay
                drawRect(Color.Black.copy(alpha = 0.6f))
                // Cut out circle
                drawCircle(
                    color = Color.Black,
                    radius = circleRadius,
                    center = center,
                    blendMode = BlendMode.DstOut
                )
                // Circle border
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = circleRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Confirm button floating at bottom
            Button(
                onClick = {
                    val cropped = cropCircle(
                        sourceBytes = imageBytes,
                        viewWidth = viewWidth.roundToInt(),
                        viewHeight = viewHeight.roundToInt(),
                        circleRadius = circleRadius,
                        scale = scale,
                        offset = offset
                    )
                    onConfirm(cropped)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Confirm",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropTopBar(
    onCancel: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                GlassTopBarTitle(
                    text = "Crop Avatar",
                    color = Color.White
                )
            },
            navigationIcon = {
                GlassTopBarBackButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

private fun clampOffset(
    offset: Offset,
    scale: Float,
    fittedWidth: Float,
    fittedHeight: Float,
    circleRadius: Float
): Offset {
    val scaledW = fittedWidth * scale
    val scaledH = fittedHeight * scale
    val maxX = (scaledW / 2f - circleRadius).coerceAtLeast(0f)
    val maxY = (scaledH / 2f - circleRadius).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}
