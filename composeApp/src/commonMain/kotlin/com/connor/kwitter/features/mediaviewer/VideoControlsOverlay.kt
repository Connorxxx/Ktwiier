package com.connor.kwitter.features.mediaviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VideoControlsOverlay(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    showControls: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onAutoHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000L)
            onAutoHide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            )
    ) {
        // Semi-transparent scrim when controls visible
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        // Center play/pause button — scale + fade
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    PauseIcon(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    PlayIcon(modifier = Modifier.size(24.dp), color = Color.White)
                }
            }
        }

        // Bottom controls — slide up + fade
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.65f)
                        )
                    )
                    .padding(top = 40.dp)
            ) {
                // Seek bar
                VideoSeekBar(
                    progress = if (durationMs > 0) {
                        currentPositionMs.toFloat() / durationMs.toFloat()
                    } else 0f,
                    onSeek = { fraction -> onSeekTo((fraction * durationMs).toLong()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Time labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 6.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress

    Canvas(
        modifier = modifier
            .height(36.dp) // generous touch target
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        dragProgress = (dragProgress + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val trackHeight = if (isDragging) 5.dp.toPx() else 3.dp.toPx()
        val trackY = (size.height - trackHeight) / 2f
        val cornerRadius = trackHeight / 2f

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )

        // Buffered / active track
        val activeWidth = size.width * displayProgress
        if (activeWidth > 0f) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, trackY),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // Thumb dot
        val thumbRadius = if (isDragging) 7.dp.toPx() else 4.5.dp.toPx()
        val thumbX = activeWidth.coerceIn(thumbRadius, size.width - thumbRadius)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(thumbX, size.height / 2f)
        )

        // Outer glow when dragging
        if (isDragging) {
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = thumbRadius + 4.dp.toPx(),
                center = Offset(thumbX, size.height / 2f)
            )
        }
    }
}

@Composable
private fun PlayIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            // Slightly offset right for visual centering of triangle
            moveTo(size.width * 0.28f, size.height * 0.15f)
            lineTo(size.width * 0.85f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.85f)
            close()
        }
        drawPath(path, color = color, style = Fill)
    }
}

@Composable
private fun PauseIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.22f
        val barHeight = size.height * 0.65f
        val barY = (size.height - barHeight) / 2f
        val cornerPx = 2.dp.toPx()
        // Left bar
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, barY),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(cornerPx)
        )
        // Right bar
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.56f, barY),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(cornerPx)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
