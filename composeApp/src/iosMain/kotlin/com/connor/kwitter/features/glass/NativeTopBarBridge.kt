@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.connor.kwitter.features.glass

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSProcessInfo
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
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
import platform.UIKit.NSTextAlignmentCenter
import platform.darwin.NSObject

private val isLiquidGlassAvailable: Boolean by lazy {
    NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion >= 26L
    }
}

private fun createGlassEffect(): UIVisualEffect =
    if (isLiquidGlassAvailable) UIGlassEffect()
    else UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial)

object NativeTopBarBridge : NativeTopBarController {
    private val _actionEvents = MutableSharedFlow<NativeTopBarAction>(extraBufferCapacity = 1)
    override val actionEvents: Flow<NativeTopBarAction> = _actionEvents

    private var topBarView: NativeOverlayTopBarView? = null
    private var desiredVisible: Boolean = false
    private var desiredDarkTheme: Boolean = false

    fun createTopBarView(): UIView {
        val view = NativeOverlayTopBarView(
            onCreatePostTap = { _actionEvents.tryEmit(NativeTopBarAction.CreatePost) },
            onProfileTap = { _actionEvents.tryEmit(NativeTopBarAction.Profile) }
        )
        topBarView = view
        view.updateTheme(desiredDarkTheme)
        view.alpha = if (desiredVisible) 1.0 else 0.0
        view.userInteractionEnabled = desiredVisible
        return view
    }

    fun setDarkTheme(isDarkTheme: Boolean) {
        desiredDarkTheme = isDarkTheme
        topBarView?.updateTheme(isDarkTheme)
    }

    override fun setTopBarVisible(visible: Boolean) {
        desiredVisible = visible
        val view = topBarView ?: return
        UIView.animateWithDuration(
            duration = 0.28,
            animations = {
                view.alpha = if (visible) 1.0 else 0.0
            }
        )
        view.userInteractionEnabled = visible
    }
}

private class NativeOverlayTopBarView(
    private val onCreatePostTap: () -> Unit,
    private val onProfileTap: () -> Unit
) : UIView(frame = CGRectZero.readValue()) {
    private val effectView = UIVisualEffectView(effect = createGlassEffect())
    private val profileButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val createButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val titleLabel = UILabel()
    private val separator = UIView()

    private val profileTapTarget = NativeTopBarTapTarget { onProfileTap() }
    private val createTapTarget = NativeTopBarTapTarget { onCreatePostTap() }

    init {
        clipsToBounds = true
        layer.cornerRadius = 24.0
        layer.maskedCorners = kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner

        addSubview(effectView)

        profileButton.apply {
            layer.cornerRadius = 20.0
            clipsToBounds = true
            setImage(UIImage.systemImageNamed("person.fill"), UIControlStateNormal)
            addTarget(
                profileTapTarget,
                NSSelectorFromString("onTap"),
                UIControlEventTouchUpInside
            )
        }
        addSubview(profileButton)

        titleLabel.apply {
            text = "Post"
            font = UIFont.boldSystemFontOfSize(19.0)
            textAlignment = NSTextAlignmentCenter
        }
        addSubview(titleLabel)

        createButton.apply {
            layer.cornerRadius = 20.0
            clipsToBounds = true
            setImage(UIImage.systemImageNamed("plus"), UIControlStateNormal)
            addTarget(
                createTapTarget,
                NSSelectorFromString("onTap"),
                UIControlEventTouchUpInside
            )
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

        val topPadding = 6.0
        val bottomPadding = 14.0
        val contentTop = safeTop + topPadding
        val contentHeight = (height - contentTop - bottomPadding).coerceAtLeast(0.0)

        val sidePadding = 14.0
        val buttonSize = 40.0
        val buttonY = contentTop + (contentHeight - buttonSize) / 2.0

        profileButton.setFrame(
            CGRectMake(sidePadding, buttonY, buttonSize, buttonSize)
        )
        createButton.setFrame(
            CGRectMake(width - sidePadding - buttonSize, buttonY, buttonSize, buttonSize)
        )

        val titleWidth = 96.0
        titleLabel.setFrame(
            CGRectMake(
                width / 2.0 - titleWidth / 2.0,
                contentTop,
                titleWidth,
                contentHeight
            )
        )

        separator.setFrame(CGRectMake(0.0, height - 0.5, width, 0.5))
    }

    fun updateTheme(isDarkTheme: Boolean) {
        val titleColor = if (isDarkTheme) {
            UIColor.whiteColor
        } else {
            UIColor.blackColor
        }
        val avatarBackground = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.12)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.06)
        }
        val avatarTint = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.9)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.8)
        }
        val createBackground = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.92)
        } else {
            UIColor.blackColor
        }
        val createTint = if (isDarkTheme) {
            UIColor.blackColor
        } else {
            UIColor.whiteColor
        }
        titleLabel.textColor = titleColor

        profileButton.backgroundColor = avatarBackground
        profileButton.tintColor = avatarTint

        createButton.backgroundColor = createBackground
        createButton.tintColor = createTint

        separator.backgroundColor = if (isDarkTheme) {
            UIColor.whiteColor.colorWithAlphaComponent(0.08)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.12)
        }
    }
}

private class NativeTopBarTapTarget(
    private val callback: () -> Unit
) : NSObject() {
    @ObjCAction
    fun onTap() {
        callback()
    }
}
