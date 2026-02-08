package com.connor.kwitter.features.mediaviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
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
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Center play/pause button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTogglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        PauseIcon(
                            modifier = Modifier.size(28.dp),
                            color = Color.White
                        )
                    } else {
                        PlayIcon(
                            modifier = Modifier.size(28.dp),
                            color = Color.White
                        )
                    }
                }

                // Bottom seekbar + time
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Slider(
                            value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                            onValueChange = { fraction ->
                                onSeekTo((fraction * durationMs).toLong())
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Text(
                            text = formatTime(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
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
            moveTo(size.width * 0.25f, size.height * 0.15f)
            lineTo(size.width * 0.85f, size.height * 0.5f)
            lineTo(size.width * 0.25f, size.height * 0.85f)
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
        val barWidth = size.width * 0.2f
        // Left bar
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.7f)
        )
        // Right bar
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.7f)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
