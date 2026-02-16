package com.connor.kwitter.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.connor.kwitter.core.theme.LocalIsDarkTheme

private val DefaultGlassTopBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultGlassTopBarShape,
    showBottomDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.48f else 0.82f)
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.34f else 0.72f),
            Color.Transparent
        )
    )

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sheenBrush)
            ) {
                content()
            }
            if (showBottomDivider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
            }
        }
    }
}

@Composable
fun GlassTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.92f else 1f)
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
    ) {
        content()
    }
}
