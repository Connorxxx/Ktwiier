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
import platform.UIKit.UIImageView
import platform.UIKit.UILabel
import platform.UIKit.UITapGestureRecognizer
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
//  Bottom Bar (iOS 26 Liquid Glass Style)
//  - Icon on top, label below
//  - Glass selection pill with spring animation
//  - Filled SF Symbols for selected, outline for unselected
// ──────────────────────────────────

private fun sfSymbolForTab(label: String, selected: Boolean): String = when (label) {
    "首页" -> if (selected) "house.fill" else "house"
    "私信" -> if (selected) "envelope.fill" else "envelope"
    "搜索" -> "magnifyingglass"
    "设置" -> if (selected) "gearshape.fill" else "gearshape"
    else -> "circle"
}

private class NativeBottomBarView : UIView(frame = CGRectZero.readValue()) {
    // Glass background
    private val effectView = UIVisualEffectView(effect = createGlassEffect())

    // Selection pill — slides between tabs with spring animation
    private val selectionPill = UIView()
    private var pillGlassView: UIVisualEffectView? = null

    // Tab items: each has icon (UIImageView) + label (UILabel)
    private val tabContainers = mutableListOf<UIView>()
    private val tabIconViews = mutableListOf<UIImageView>()
    private val tabLabelViews = mutableListOf<UILabel>()
    private val tabTapTargets = mutableListOf<TapActionTarget>()

    // State
    private var lastSelectedIndex = -1
    private var isDark = false

    init {
        clipsToBounds = true
        layer.cornerRadius = 31.0

        addSubview(effectView)

        // Selection pill (sits above glass background, below tab content)
        selectionPill.clipsToBounds = true
        selectionPill.layer.cornerRadius = 24.0
        addSubview(selectionPill)

        if (isLiquidGlassAvailable) {
            val glassView = UIVisualEffectView(effect = UIGlassEffect())
            glassView.clipsToBounds = true
            glassView.layer.cornerRadius = 24.0
            selectionPill.addSubview(glassView)
            pillGlassView = glassView
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        effectView.setFrame(bounds)

        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val hPad = 6.0
        val vPad = 6.0
        val spacing = 4.0
        val count = tabContainers.size
        if (count == 0) return

        val availableWidth = width - hPad * 2.0 - spacing * (count - 1).coerceAtLeast(0)
        val tabWidth = (availableWidth / count).coerceAtLeast(0.0)
        val tabHeight = (height - vPad * 2.0).coerceAtLeast(0.0)

        // Layout tab containers
        tabContainers.forEachIndexed { i, container ->
            val x = hPad + i * (tabWidth + spacing)
            container.setFrame(CGRectMake(x, vPad, tabWidth, tabHeight))

            // Icon + label layout within container
            val iconSize = 22.0
            val labelHeight = 13.0
            val gap = 2.0
            val totalContentHeight = iconSize + gap + labelHeight
            val contentTop = (tabHeight - totalContentHeight) / 2.0

            tabIconViews[i].setFrame(
                CGRectMake((tabWidth - iconSize) / 2.0, contentTop, iconSize, iconSize)
            )
            tabLabelViews[i].setFrame(
                CGRectMake(0.0, contentTop + iconSize + gap, tabWidth, labelHeight)
            )
        }

        // Position pill (non-animated, for layout changes like rotation)
        if (lastSelectedIndex in tabContainers.indices) {
            val pillFrame = pillFrameForIndex(lastSelectedIndex)
            selectionPill.setFrame(pillFrame)
            pillGlassView?.setFrame(
                CGRectMake(0.0, 0.0, pillFrame.useContents { size.width }, pillFrame.useContents { size.height })
            )
        }
    }

    fun update(
        isDarkTheme: Boolean,
        tabLabels: List<String>,
        selectedIndex: Int,
        onTabSelected: (Int) -> Unit
    ) {
        isDark = isDarkTheme
        ensureTabCount(tabLabels.size.coerceAtLeast(1))

        val activeColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.92)
        } else UIColor.blueColor

        val inactiveColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.4)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.35)
        }

        // Pill tint (only visible when not using glass pill)
        if (pillGlassView == null) {
            selectionPill.backgroundColor = if (isDarkTheme) {
                UIColor.whiteColor.colorWithAlphaComponent(0.12)
            } else {
                UIColor.blueColor.colorWithAlphaComponent(0.12)
            }
        }

        // Update each tab's icon + label
        tabLabelViews.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val tabText = tabLabels.getOrNull(i).orEmpty()

            // Icon (filled variant when selected)
            tabIconViews[i].image = UIImage.systemImageNamed(sfSymbolForTab(tabText, selected))
            tabIconViews[i].tintColor = if (selected) activeColor else inactiveColor

            // Label
            label.text = tabText
            label.textColor = if (selected) activeColor else inactiveColor
            label.font = if (selected) {
                UIFont.boldSystemFontOfSize(10.0)
            } else {
                UIFont.systemFontOfSize(10.0)
            }

            tabTapTargets[i].action = { onTabSelected(i) }
        }

        // Animate selection pill
        val shouldAnimate = lastSelectedIndex >= 0 && lastSelectedIndex != selectedIndex
        lastSelectedIndex = selectedIndex
        animatePill(toIndex = selectedIndex, animated = shouldAnimate)
    }

    // ── Selection Pill ──

    private fun pillFrameForIndex(index: Int): kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect> {
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val hPad = 6.0
        val vPad = 6.0
        val spacing = 4.0
        val count = tabContainers.size
        if (count == 0) return CGRectZero.readValue()

        val availableWidth = width - hPad * 2.0 - spacing * (count - 1).coerceAtLeast(0)
        val tabWidth = (availableWidth / count).coerceAtLeast(0.0)
        val tabHeight = (height - vPad * 2.0).coerceAtLeast(0.0)
        val x = hPad + index * (tabWidth + spacing)
        return CGRectMake(x, vPad, tabWidth, tabHeight)
    }

    private fun animatePill(toIndex: Int, animated: Boolean) {
        if (toIndex !in tabContainers.indices) return

        val targetFrame = pillFrameForIndex(toIndex)
        val tw = targetFrame.useContents { size.width }
        val th = targetFrame.useContents { size.height }

        if (animated) {
            UIView.animateWithDuration(
                duration = 0.5,
                delay = 0.0,
                usingSpringWithDamping = 0.72,
                initialSpringVelocity = 0.3,
                options = 0u,
                animations = {
                    selectionPill.setFrame(targetFrame)
                    pillGlassView?.setFrame(CGRectMake(0.0, 0.0, tw, th))
                },
                completion = null
            )
        } else {
            selectionPill.setFrame(targetFrame)
            pillGlassView?.setFrame(CGRectMake(0.0, 0.0, tw, th))
        }
    }

    // ── Tab Management ──

    private fun ensureTabCount(target: Int) {
        while (tabContainers.size < target) {
            val tap = TapActionTarget()
            val container = UIView()
            container.userInteractionEnabled = true

            val icon = UIImageView()
            icon.tintColor = UIColor.grayColor

            val label = UILabel()
            label.textAlignment = NSTextAlignmentCenter
            label.font = UIFont.systemFontOfSize(10.0)

            container.addSubview(icon)
            container.addSubview(label)

            val tapGesture = UITapGestureRecognizer(target = tap, action = NSSelectorFromString("onTap"))
            container.addGestureRecognizer(tapGesture)

            addSubview(container)
            tabContainers += container
            tabIconViews += icon
            tabLabelViews += label
            tabTapTargets += tap
        }
        while (tabContainers.size > target) {
            tabContainers.removeLast().removeFromSuperview()
            tabIconViews.removeLast()
            tabLabelViews.removeLast()
            tabTapTargets.removeLast().action = null
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
