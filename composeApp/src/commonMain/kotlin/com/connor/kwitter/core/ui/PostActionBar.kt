package com.connor.kwitter.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PostActionBar(
    replyCount: Int,
    likeCount: Int,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ActionButton(
            onClick = onReplyClick,
            icon = { color -> ReplyBubbleIcon(color = color) },
            count = replyCount,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        ActionButton(
            onClick = onLikeClick,
            icon = { color -> HeartIcon(color = color, filled = isLiked) },
            count = likeCount,
            contentColor = if (isLiked) LikeActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        ActionButton(
            onClick = onBookmarkClick,
            icon = { color -> BookmarkIcon(color = color, filled = isBookmarked) },
            count = null,
            contentColor = if (isBookmarked) BookmarkActiveColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    count: Int?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon(contentColor)
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ReplyBubbleIcon(
    modifier: Modifier = Modifier.size(18.dp),
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.08f
        val bubbleLeft = size.width * 0.1f
        val bubbleTop = size.height * 0.1f
        val bubbleRight = size.width * 0.9f
        val bubbleBottom = size.height * 0.65f
        val bubbleWidth = bubbleRight - bubbleLeft
        val bubbleHeight = bubbleBottom - bubbleTop

        drawRoundRect(
            color = color,
            topLeft = Offset(bubbleLeft, bubbleTop),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(size.minDimension * 0.15f),
            style = Stroke(width = stroke)
        )

        val tailPath = Path().apply {
            moveTo(size.width * 0.25f, bubbleBottom)
            lineTo(size.width * 0.2f, size.height * 0.88f)
            lineTo(size.width * 0.45f, bubbleBottom)
        }
        drawPath(tailPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun HeartIcon(
    modifier: Modifier = Modifier.size(18.dp),
    color: Color,
    filled: Boolean
) {
    Canvas(modifier = modifier) {
        drawHeart(color, filled)
    }
}

private fun DrawScope.drawHeart(color: Color, filled: Boolean) {
    val path = Path().apply {
        val w = size.width
        val h = size.height

        moveTo(w * 0.5f, h * 0.85f)

        cubicTo(
            w * 0.15f, h * 0.6f,
            w * 0.0f, h * 0.35f,
            w * 0.15f, h * 0.2f
        )
        cubicTo(
            w * 0.28f, h * 0.08f,
            w * 0.45f, h * 0.12f,
            w * 0.5f, h * 0.32f
        )
        cubicTo(
            w * 0.55f, h * 0.12f,
            w * 0.72f, h * 0.08f,
            w * 0.85f, h * 0.2f
        )
        cubicTo(
            w * 1.0f, h * 0.35f,
            w * 0.85f, h * 0.6f,
            w * 0.5f, h * 0.85f
        )
        close()
    }

    if (filled) {
        drawPath(path, color = color, style = Fill)
    } else {
        drawPath(path, color = color, style = Stroke(width = size.minDimension * 0.08f))
    }
}

@Composable
private fun BookmarkIcon(
    modifier: Modifier = Modifier.size(18.dp),
    color: Color,
    filled: Boolean
) {
    Canvas(modifier = modifier) {
        drawBookmark(color, filled)
    }
}

private fun DrawScope.drawBookmark(color: Color, filled: Boolean) {
    val path = Path().apply {
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        val top = size.height * 0.08f
        val bottom = size.height * 0.92f
        val midX = size.width * 0.5f
        val notchY = size.height * 0.72f

        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(midX, notchY)
        lineTo(left, bottom)
        close()
    }

    if (filled) {
        drawPath(path, color = color, style = Fill)
    } else {
        drawPath(path, color = color, style = Stroke(width = size.minDimension * 0.08f))
    }
}

private val LikeActiveColor = Color(0xFFE0245E)
private val BookmarkActiveColor = Color(0xFF1DA1F2)
