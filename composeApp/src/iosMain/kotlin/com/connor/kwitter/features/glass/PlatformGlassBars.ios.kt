@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.connor.kwitter.features.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSProcessInfo
import platform.QuartzCore.kCALayerMaxXMaxYCorner
import platform.QuartzCore.kCALayerMinXMaxYCorner
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.NSTextAlignmentCenter
import platform.darwin.NSObject

// ──────────────────────────────────
//  iOS Version Detection
// ──────────────────────────────────

private val isLiquidGlassAvailable: Boolean by lazy {
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion >= 26L
    }
}

private fun createGlassEffect(): UIVisualEffect =
    if (isLiquidGlassAvailable) UIGlassEffect()
    else UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial)

// ──────────────────────────────────
//  Expect/Actual
// ──────────────────────────────────

actual fun supportsNativeGlassBars(): Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun NativeGlassTopBar(
    modifier: Modifier,
    isDarkTheme: Boolean,
    onCreatePostClick: () -> Unit,
    onProfileClick: (() -> Unit)?
) {
    UIKitView(
        modifier = modifier,
        factory = { NativeTopBarView() },
        update = { view ->
            view.update(isDarkTheme, onCreatePostClick, onProfileClick)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
            placedAsOverlay = true
        )
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun NativeGlassBottomBar(
    modifier: Modifier,
    isDarkTheme: Boolean,
    tabLabels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    UIKitView(
        modifier = modifier,
        factory = { NativeBottomBarView() },
        update = { view ->
            view.update(isDarkTheme, tabLabels, selectedIndex, onTabSelected)
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
            placedAsOverlay = true
        )
    )
}

// ──────────────────────────────────
//  Top Bar
// ──────────────────────────────────

private class NativeTopBarView : UIView(frame = CGRectZero.readValue()) {
    private val effectView = UIVisualEffectView(effect = createGlassEffect())
    private val profileButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val createButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val titleLabel = UILabel()
    private val separator = UIView()

    private val profileTap = TapActionTarget()
    private val createTap = TapActionTarget()

    init {
        clipsToBounds = true
        layer.cornerRadius = 22.0
        layer.maskedCorners = kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner

        addSubview(effectView)

        profileButton.apply {
            layer.cornerRadius = 18.0
            clipsToBounds = true
            setImage(UIImage.systemImageNamed("person.fill"), UIControlStateNormal)
            addTarget(profileTap, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)
        }
        addSubview(profileButton)

        titleLabel.apply {
            text = "K"
            textAlignment = NSTextAlignmentCenter
            font = UIFont.boldSystemFontOfSize(26.0)
        }
        addSubview(titleLabel)

        createButton.apply {
            layer.cornerRadius = 18.0
            clipsToBounds = true
            setImage(UIImage.systemImageNamed("plus"), UIControlStateNormal)
            addTarget(createTap, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)
        }
        addSubview(createButton)

        addSubview(separator)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        effectView.setFrame(bounds)

        val safeTop = safeAreaInsets.useContents { top }
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }

        val bottomPadding = 10.0
        val contentTop = safeTop
        val contentHeight = (height - contentTop - bottomPadding).coerceAtLeast(0.0)
        val sidePadding = 16.0
        val buttonSize = 36.0
        val buttonY = contentTop + (contentHeight - buttonSize) / 2.0

        profileButton.setFrame(
            CGRectMake(sidePadding, buttonY, buttonSize, buttonSize)
        )
        createButton.setFrame(
            CGRectMake(width - sidePadding - buttonSize, buttonY, buttonSize, buttonSize)
        )

        val labelX = sidePadding + buttonSize + 8.0
        titleLabel.setFrame(
            CGRectMake(labelX, contentTop, width - labelX * 2.0, contentHeight)
        )

        separator.setFrame(CGRectMake(0.0, height - 0.5, width, 0.5))
    }

    fun update(
        isDarkTheme: Boolean,
        onCreatePostClick: () -> Unit,
        onProfileClick: (() -> Unit)?
    ) {
        createTap.action = onCreatePostClick
        profileTap.action = onProfileClick

        val accentColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.9)
        } else UIColor.blueColor

        val contentColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.85)
        } else UIColor.blackColor

        val buttonBg = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.1)
        } else {
            UIColor.grayColor.colorWithAlphaComponent(0.1)
        }

        titleLabel.textColor = accentColor

        createButton.tintColor = accentColor
        createButton.backgroundColor = buttonBg

        profileButton.tintColor = contentColor
        profileButton.backgroundColor = buttonBg

        separator.backgroundColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.08)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.12)
        }

        profileButton.userInteractionEnabled = onProfileClick != null
        profileButton.alpha = if (onProfileClick != null) 1.0 else 0.4
    }
}

// ──────────────────────────────────
//  Bottom Bar
// ──────────────────────────────────

private class NativeBottomBarView : UIView(frame = CGRectZero.readValue()) {
    private val effectView = UIVisualEffectView(effect = createGlassEffect())
    private val buttons = mutableListOf<UIButton>()
    private val tapTargets = mutableListOf<TapActionTarget>()

    init {
        clipsToBounds = true
        layer.cornerRadius = 31.0
        addSubview(effectView)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        effectView.setFrame(bounds)

        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val hPad = 6.0
        val vPad = 6.0
        val spacing = 4.0
        val count = buttons.size
        if (count == 0) return

        val availableWidth = width - hPad * 2.0 - spacing * (count - 1).coerceAtLeast(0)
        val buttonWidth = (availableWidth / count).coerceAtLeast(0.0)
        val buttonHeight = (height - vPad * 2.0).coerceAtLeast(0.0)

        buttons.forEachIndexed { i, button ->
            button.setFrame(
                CGRectMake(
                    hPad + i * (buttonWidth + spacing),
                    vPad,
                    buttonWidth,
                    buttonHeight
                )
            )
            button.layer.cornerRadius = 22.0
        }
    }

    fun update(
        isDarkTheme: Boolean,
        tabLabels: List<String>,
        selectedIndex: Int,
        onTabSelected: (Int) -> Unit
    ) {
        ensureButtonCount(tabLabels.size.coerceAtLeast(1))

        val activeColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.92)
        } else UIColor.blueColor

        val inactiveColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.4)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.35)
        }

        val selectedBg = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.12)
        } else {
            UIColor.blueColor.colorWithAlphaComponent(0.12)
        }

        buttons.forEachIndexed { i, button ->
            val selected = i == selectedIndex
            button.setTitle(tabLabels.getOrNull(i).orEmpty(), UIControlStateNormal)
            button.setTitleColor(
                if (selected) activeColor else inactiveColor,
                UIControlStateNormal
            )
            button.backgroundColor = if (selected) selectedBg else UIColor.clearColor
            button.titleLabel?.font = if (selected) {
                UIFont.boldSystemFontOfSize(14.0)
            } else {
                UIFont.systemFontOfSize(14.0)
            }
            tapTargets[i].action = { onTabSelected(i) }
        }
        setNeedsLayout()
    }

    private fun ensureButtonCount(target: Int) {
        while (buttons.size < target) {
            val tap = TapActionTarget()
            val button = UIButton.buttonWithType(UIButtonTypeSystem)
            button.clipsToBounds = true
            button.addTarget(tap, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)
            addSubview(button)
            buttons += button
            tapTargets += tap
        }
        while (buttons.size > target) {
            buttons.removeLast().removeFromSuperview()
            tapTargets.removeLast().action = null
        }
    }
}

// ──────────────────────────────────
//  Tap Action Bridge
// ──────────────────────────────────

private class TapActionTarget : NSObject() {
    var action: (() -> Unit)? = null

    @ObjCAction
    fun onTap() {
        action?.invoke()
    }
}
