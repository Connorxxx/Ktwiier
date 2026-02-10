package com.connor.kwitter.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun BackArrowIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val centerY = size.height * 0.5f
        val left = size.width * 0.15f
        val right = size.width * 0.85f
        val arrowSize = size.width * 0.25f
        // Horizontal line
        drawLine(resolvedColor, Offset(left, centerY), Offset(right, centerY), stroke, cap = StrokeCap.Round)
        // Arrow head
        drawLine(resolvedColor, Offset(left, centerY), Offset(left + arrowSize, centerY - arrowSize), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(left, centerY), Offset(left + arrowSize, centerY + arrowSize), stroke, cap = StrokeCap.Round)
    }
}
