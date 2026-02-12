package com.connor.kwitter.features.mediaviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ZoomableImageViewer(
    imageUrl: String,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                // Animate back to 1x
                                launch {
                                    scale.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                launch {
                                    offsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                onZoomChanged(false)
                            } else {
                                // Zoom into tap point with animation
                                val targetScale = 2.5f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val maxX = (size.width * (targetScale - 1f)) / 2f
                                val maxY = (size.height * (targetScale - 1f)) / 2f
                                val targetOffsetX = ((centerX - tapOffset.x) * (targetScale - 1f))
                                    .coerceIn(-maxX, maxX)
                                val targetOffsetY = ((centerY - tapOffset.y) * (targetScale - 1f))
                                    .coerceIn(-maxY, maxY)

                                val animSpec = spring<Float>(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                                launch { scale.animateTo(targetScale, animSpec) }
                                launch { offsetX.animateTo(targetOffsetX, animSpec) }
                                launch { offsetY.animateTo(targetOffsetY, animSpec) }
                                onZoomChanged(true)
                            }
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (zoomChange != 1f) {
                            // Pinch zoom — immediate response via snapTo
                            val newScale = (scale.value * zoomChange).coerceIn(0.5f, 5f)
                            val maxX = (w * (newScale - 1f).coerceAtLeast(0f)) / 2f
                            val maxY = (h * (newScale - 1f).coerceAtLeast(0f)) / 2f
                            val newOffsetX = (offsetX.value + panChange.x).coerceIn(-maxX, maxX)
                            val newOffsetY = (offsetY.value + panChange.y).coerceIn(-maxY, maxY)

                            scope.launch {
                                scale.snapTo(newScale)
                                offsetX.snapTo(newOffsetX)
                                offsetY.snapTo(newOffsetY)
                            }
                            onZoomChanged(newScale > 1f)
                            event.changes.forEach { it.consume() }
                        } else if (scale.value > 1f) {
                            // Single-finger pan when zoomed
                            val maxX = (w * (scale.value - 1f)) / 2f
                            val maxY = (h * (scale.value - 1f)) / 2f
                            val newOffsetX = (offsetX.value + panChange.x).coerceIn(-maxX, maxX)
                            val newOffsetY = (offsetY.value + panChange.y).coerceIn(-maxY, maxY)

                            scope.launch {
                                offsetX.snapTo(newOffsetX)
                                offsetY.snapTo(newOffsetY)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        // scale <= 1f with no zoom change → don't consume, pager handles swipe
                    } while (event.changes.any { it.pressed })

                    // Gesture released — spring back if over-pinched below 1x
                    if (scale.value < 1f) {
                        scope.launch {
                            val animSpec = spring<Float>(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            launch { scale.animateTo(1f, animSpec) }
                            launch { offsetX.animateTo(0f, animSpec) }
                            launch { offsetY.animateTo(0f, animSpec) }
                            onZoomChanged(false)
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offsetX.value
                translationY = offsetY.value
            }
    )
}
