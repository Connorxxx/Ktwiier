package com.connor.kwitter.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

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

@Composable
fun EditPenIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f

        // Pen body (diagonal line from bottom-left to upper-right)
        val penTipX = size.width * 0.15f
        val penTipY = size.height * 0.85f
        val penTopX = size.width * 0.75f
        val penTopY = size.height * 0.25f

        // Pen body outline
        val halfWidth = size.minDimension * 0.08f
        val dx = penTopX - penTipX
        val dy = penTopY - penTipY
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val nx = -dy / len * halfWidth
        val ny = dx / len * halfWidth

        val bodyPath = Path().apply {
            // Pen tip (pointed)
            moveTo(penTipX, penTipY)
            lineTo(penTopX + nx, penTopY + ny)
            lineTo(penTopX - nx, penTopY - ny)
            close()
        }
        drawPath(bodyPath, resolvedColor)

        // Eraser cap (small rectangle at the top end)
        val capStartX = penTopX
        val capStartY = penTopY
        val capLen = size.minDimension * 0.15f
        val capEndX = capStartX + dx / len * capLen
        val capEndY = capStartY + dy / len * capLen

        val capPath = Path().apply {
            moveTo(capStartX + nx, capStartY + ny)
            lineTo(capStartX - nx, capStartY - ny)
            lineTo(capEndX - nx, capEndY - ny)
            lineTo(capEndX + nx, capEndY + ny)
            close()
        }
        drawPath(capPath, resolvedColor)

        // Small edit line at bottom-left (indicates writing surface)
        drawLine(
            resolvedColor,
            Offset(penTipX - size.width * 0.02f, penTipY + size.height * 0.04f),
            Offset(penTipX + size.width * 0.2f, penTipY + size.height * 0.04f),
            stroke,
            cap = StrokeCap.Round
        )
    }
}
