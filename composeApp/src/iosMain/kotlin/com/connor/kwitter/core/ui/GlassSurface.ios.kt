@file:OptIn(ExperimentalForeignApi::class)

package com.connor.kwitter.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.QuartzCore.kCALayerMaxXMaxYCorner
import platform.QuartzCore.kCALayerMaxXMinYCorner
import platform.QuartzCore.kCALayerMinXMaxYCorner
import platform.QuartzCore.kCALayerMinXMinYCorner
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView

private val isLiquidGlassAvailable: Boolean by lazy {
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion >= 26L
    }
}

private fun createGlassEffect(): UIVisualEffect =
    if (isLiquidGlassAvailable) UIGlassEffect()
    else UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial)

@Composable
actual fun GlassSurface(
    modifier: Modifier,
    shape: Shape,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val allCorners = kCALayerMinXMinYCorner or kCALayerMaxXMinYCorner or
            kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner

    var cornerRadiusPt by remember { mutableStateOf(0.0) }
    var maskedCorners by remember { mutableStateOf(allCorners) }

    Box(
        modifier = modifier
            .clip(shape)
            .onSizeChanged { size ->
                val outline = shape.createOutline(
                    size = Size(size.width.toFloat(), size.height.toFloat()),
                    layoutDirection = layoutDirection,
                    density = density
                )
                when (outline) {
                    is Outline.Rounded -> {
                        val rr = outline.roundRect
                        val tl = rr.topLeftCornerRadius.x
                        val tr = rr.topRightCornerRadius.x
                        val bl = rr.bottomLeftCornerRadius.x
                        val br = rr.bottomRightCornerRadius.x

                        cornerRadiusPt = (maxOf(tl, tr, bl, br) / density.density).toDouble()

                        var mask = 0uL
                        if (tl > 0f) mask = mask or kCALayerMinXMinYCorner
                        if (tr > 0f) mask = mask or kCALayerMaxXMinYCorner
                        if (bl > 0f) mask = mask or kCALayerMinXMaxYCorner
                        if (br > 0f) mask = mask or kCALayerMaxXMaxYCorner
                        maskedCorners = mask
                    }
                    else -> {
                        cornerRadiusPt = 0.0
                        maskedCorners = allCorners
                    }
                }
            }
    ) {
        UIKitView(
            modifier = Modifier.matchParentSize(),
            factory = {
                UIVisualEffectView(effect = createGlassEffect()).apply {
                    clipsToBounds = true
                }
            },
            update = { view ->
                view.layer.cornerRadius = cornerRadiusPt
                view.layer.maskedCorners = maskedCorners
            },
            properties = UIKitInteropProperties(interactionMode = null)
        )
        content()
    }
}
