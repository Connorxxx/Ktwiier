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
import platform.QuartzCore.kCALayerMaxXMaxYCorner
import platform.QuartzCore.kCALayerMinXMaxYCorner
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIFont
import platform.darwin.NSObject

actual fun supportsNativeGlassBars(): Boolean = true

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
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
            view.update(
                isDarkTheme = isDarkTheme,
                onCreatePostClick = onCreatePostClick,
                onProfileClick = onProfileClick
            )
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
            placedAsOverlay = true
        )
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
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
            view.update(
                isDarkTheme = isDarkTheme,
                tabLabels = tabLabels,
                selectedIndex = selectedIndex,
                onTabSelected = onTabSelected
            )
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
            placedAsOverlay = true
        )
    )
}

private class NativeTopBarView : UIView(frame = CGRectZero.readValue()) {
    private val blurView = UIVisualEffectView(
        effect = UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial)
    )
    private val profileButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val createButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val titleLabel = UILabel()
    private val bottomSeparator = UIView()

    private val profileTapTarget = TapActionTarget()
    private val createTapTarget = TapActionTarget()

    init {
        clipsToBounds = true
        layer.cornerRadius = 20.0
        layer.maskedCorners = kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner

        profileButton.layer.cornerRadius = 20.0
        createButton.layer.cornerRadius = 20.0
        profileButton.clipsToBounds = true
        createButton.clipsToBounds = true

        titleLabel.text = "K"
        titleLabel.textAlignment = NSTextAlignmentCenter
        titleLabel.font = UIFont.boldSystemFontOfSize(28.0)

        profileButton.setTitle("●", UIControlStateNormal)
        createButton.setTitle("+", UIControlStateNormal)
        createButton.titleLabel?.font = UIFont.boldSystemFontOfSize(22.0)

        profileButton.addTarget(profileTapTarget, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)
        createButton.addTarget(createTapTarget, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)

        addSubview(blurView)
        addSubview(profileButton)
        addSubview(createButton)
        addSubview(titleLabel)
        addSubview(bottomSeparator)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        blurView.setFrame(bounds)

        val safeTop = safeAreaInsets.useContents { top }
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val sidePadding = 12.0
        val contentTop = safeTop + 6.0
        val contentHeight = (height - safeTop - 8.0).coerceAtLeast(44.0)
        val buttonSize = 40.0
        val buttonY = contentTop + ((contentHeight - buttonSize) / 2.0)

        profileButton.setFrame(CGRectMake(sidePadding, buttonY, buttonSize, buttonSize))
        createButton.setFrame(CGRectMake(width - sidePadding - buttonSize, buttonY, buttonSize, buttonSize))
        titleLabel.setFrame(CGRectMake(
            sidePadding + buttonSize + 12.0,
            contentTop,
            width - (sidePadding + buttonSize + 12.0) * 2.0,
            contentHeight
        ))
        bottomSeparator.setFrame(CGRectMake(0.0, height - 1.0, width, 1.0))
    }

    fun update(
        isDarkTheme: Boolean,
        onCreatePostClick: () -> Unit,
        onProfileClick: (() -> Unit)?
    ) {
        createTapTarget.action = onCreatePostClick
        profileTapTarget.action = onProfileClick

        val primaryColor = if (isDarkTheme) UIColor.whiteColor else UIColor.blueColor
        val secondaryColor = if (isDarkTheme) UIColor.whiteColor else UIColor.blackColor
        val chipColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.18)
        } else {
            UIColor.whiteColor.colorWithAlphaComponent(0.55)
        }
        val separatorColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.14)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.16)
        }

        titleLabel.textColor = primaryColor
        createButton.setTitleColor(primaryColor, UIControlStateNormal)
        profileButton.setTitleColor(secondaryColor, UIControlStateNormal)
        profileButton.backgroundColor = chipColor
        createButton.backgroundColor = chipColor
        bottomSeparator.backgroundColor = separatorColor

        val hasProfileAction = onProfileClick != null
        profileButton.userInteractionEnabled = hasProfileAction
        profileButton.alpha = if (hasProfileAction) 1.0 else 0.5
    }
}

private class NativeBottomBarView : UIView(frame = CGRectZero.readValue()) {
    private val blurView = UIVisualEffectView(
        effect = UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial)
    )
    private val buttons = mutableListOf<UIButton>()
    private val tapTargets = mutableListOf<TapActionTarget>()

    init {
        clipsToBounds = true
        layer.cornerRadius = 31.0
        addSubview(blurView)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        blurView.setFrame(bounds)

        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val horizontalPadding = 8.0
        val verticalPadding = 8.0
        val spacing = 6.0
        val availableWidth = (width - horizontalPadding * 2.0 - spacing * (buttons.size - 1))
            .coerceAtLeast(0.0)
        val buttonWidth = if (buttons.isNotEmpty()) availableWidth / buttons.size else 0.0
        val buttonHeight = (height - verticalPadding * 2.0).coerceAtLeast(0.0)

        buttons.forEachIndexed { index, button ->
            button.setFrame(CGRectMake(
                horizontalPadding + index * (buttonWidth + spacing),
                verticalPadding,
                buttonWidth,
                buttonHeight
            ))
            button.layer.cornerRadius = 20.0
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
        } else {
            UIColor.blueColor
        }
        val inactiveColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.45)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.35)
        }
        val selectedBackground = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.12)
        } else {
            UIColor.blueColor.colorWithAlphaComponent(0.14)
        }

        buttons.forEachIndexed { index, button ->
            val selected = index == selectedIndex
            val label = tabLabels.getOrNull(index).orEmpty()
            button.setTitle(label, UIControlStateNormal)
            button.setTitleColor(
                if (selected) activeColor else inactiveColor,
                UIControlStateNormal
            )
            button.backgroundColor = if (selected) selectedBackground else UIColor.clearColor
            button.titleLabel?.font = if (selected) {
                UIFont.boldSystemFontOfSize(14.0)
            } else {
                UIFont.systemFontOfSize(14.0)
            }
            tapTargets[index].action = { onTabSelected(index) }
        }

        setNeedsLayout()
    }

    private fun ensureButtonCount(targetCount: Int) {
        while (buttons.size < targetCount) {
            val target = TapActionTarget()
            val button = UIButton.buttonWithType(UIButtonTypeSystem)
            button.addTarget(target, NSSelectorFromString("onTap"), UIControlEventTouchUpInside)
            button.clipsToBounds = true
            addSubview(button)
            buttons += button
            tapTargets += target
        }

        while (buttons.size > targetCount) {
            buttons.removeLast().removeFromSuperview()
            tapTargets.removeLast().action = null
        }
    }
}

private class TapActionTarget : NSObject() {
    var action: (() -> Unit)? = null

    @ObjCAction
    fun onTap() {
        action?.invoke()
    }
}
