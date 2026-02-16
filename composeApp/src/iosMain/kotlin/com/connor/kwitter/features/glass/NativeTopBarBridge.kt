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
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIControlEventEditingDidEndOnExit
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UITextField
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffect
import platform.UIKit.UIVisualEffectView
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
    private val _actionEvents = MutableSharedFlow<NativeTopBarAction>(extraBufferCapacity = 4)
    override val actionEvents: Flow<NativeTopBarAction> = _actionEvents

    private var topBarView: NativeOverlayTopBarView? = null
    private var desiredModel: NativeTopBarModel = NativeTopBarModel.Hidden
    private var desiredDarkTheme: Boolean = false

    fun createTopBarView(): UIView {
        val view = NativeOverlayTopBarView(onAction = { action ->
            _actionEvents.tryEmit(action)
        })
        topBarView = view
        view.updateTheme(desiredDarkTheme)
        view.updateModel(desiredModel)
        view.alpha = if (desiredModel is NativeTopBarModel.Hidden) 0.0 else 1.0
        view.userInteractionEnabled = desiredModel !is NativeTopBarModel.Hidden
        return view
    }

    fun setDarkTheme(isDarkTheme: Boolean) {
        desiredDarkTheme = isDarkTheme
        topBarView?.updateTheme(isDarkTheme)
    }

    override fun setModel(model: NativeTopBarModel) {
        desiredModel = model
        val view = topBarView ?: return
        view.updateModel(model)

        val shouldHide = model is NativeTopBarModel.Hidden
        val isCurrentlyHidden = view.alpha < 0.01
        if (isCurrentlyHidden != shouldHide) {
            UIView.animateWithDuration(
                duration = 0.28,
                animations = {
                    view.alpha = if (shouldHide) 0.0 else 1.0
                }
            )
        } else {
            view.alpha = if (shouldHide) 0.0 else 1.0
        }
        view.userInteractionEnabled = !shouldHide
    }
}

private class NativeOverlayTopBarView(
    private val onAction: (NativeTopBarAction) -> Unit
) : UIView(frame = CGRectZero.readValue()) {
    private val effectView = UIVisualEffectView(effect = createGlassEffect())
    private val leadingButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val trailingIconButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val trailingTextButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val titleLabel = UILabel()
    private val subtitleLabel = UILabel()
    private val searchField = UITextField()
    private val separator = UIView()

    private var currentModel: NativeTopBarModel = NativeTopBarModel.Hidden
    private var isDarkTheme: Boolean = false
    private var preferLightForeground: Boolean = false

    private var leadingAction: NativeTopBarButtonAction? = null
    private var trailingAction: NativeTopBarButtonAction? = null

    private val leadingTapTarget = NativeTopBarTapTarget {
        leadingAction?.let { action ->
            onAction(NativeTopBarAction.ButtonClicked(action))
        }
    }
    private val trailingTapTarget = NativeTopBarTapTarget {
        trailingAction?.let { action ->
            onAction(NativeTopBarAction.ButtonClicked(action))
        }
    }
    private val searchChangeTarget = NativeTopBarTapTarget {
        onAction(NativeTopBarAction.SearchQueryChanged(searchField.text ?: ""))
    }
    private val searchSubmitTarget = NativeTopBarTapTarget {
        onAction(NativeTopBarAction.SearchSubmitted)
    }

    init {
        clipsToBounds = true
        layer.cornerRadius = 24.0
        layer.maskedCorners = kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner

        addSubview(effectView)

        leadingButton.addTarget(
            leadingTapTarget,
            NSSelectorFromString("onTap"),
            UIControlEventTouchUpInside
        )
        addSubview(leadingButton)

        trailingIconButton.addTarget(
            trailingTapTarget,
            NSSelectorFromString("onTap"),
            UIControlEventTouchUpInside
        )
        addSubview(trailingIconButton)

        trailingTextButton.layer.cornerRadius = 16.0
        trailingTextButton.clipsToBounds = true
        trailingTextButton.titleLabel?.font = UIFont.boldSystemFontOfSize(15.0)
        trailingTextButton.addTarget(
            trailingTapTarget,
            NSSelectorFromString("onTap"),
            UIControlEventTouchUpInside
        )
        addSubview(trailingTextButton)

        titleLabel.apply {
            font = UIFont.boldSystemFontOfSize(19.0)
            textAlignment = NSTextAlignmentCenter
            adjustsFontSizeToFitWidth = true
            minimumScaleFactor = 0.82
        }
        addSubview(titleLabel)

        subtitleLabel.apply {
            font = UIFont.systemFontOfSize(12.0)
            textAlignment = NSTextAlignmentCenter
            adjustsFontSizeToFitWidth = true
            minimumScaleFactor = 0.85
        }
        addSubview(subtitleLabel)

        searchField.apply {
            layer.cornerRadius = 22.0
            layer.borderWidth = 1.0
            clipsToBounds = true
            font = UIFont.systemFontOfSize(17.0)
            addTarget(
                searchChangeTarget,
                NSSelectorFromString("onTap"),
                UIControlEventEditingChanged
            )
            addTarget(
                searchSubmitTarget,
                NSSelectorFromString("onTap"),
                UIControlEventEditingDidEndOnExit
            )
        }
        addSubview(searchField)

        addSubview(separator)

        setAllElementsHidden()
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

        when (currentModel) {
            NativeTopBarModel.Hidden -> Unit
            NativeTopBarModel.HomeInteractive -> layoutHome(width, contentTop, contentHeight, sidePadding)
            is NativeTopBarModel.Title -> layoutTitle(width, contentTop, contentHeight, sidePadding)
            is NativeTopBarModel.Search -> layoutSearch(width, contentTop, contentHeight, sidePadding)
        }

        separator.hidden = currentModel is NativeTopBarModel.Hidden
        if (!separator.hidden) {
            separator.setFrame(CGRectMake(0.0, height - 0.5, width, 0.5))
        }
    }

    fun updateModel(model: NativeTopBarModel) {
        val previousModel = currentModel
        currentModel = model
        setAllElementsHidden()

        when (model) {
            NativeTopBarModel.Hidden -> {
                preferLightForeground = false
                leadingAction = null
                trailingAction = null
            }

            NativeTopBarModel.HomeInteractive -> {
                preferLightForeground = false
                titleLabel.hidden = false
                titleLabel.text = "Post"
                subtitleLabel.hidden = true

                leadingAction = NativeTopBarButtonAction.Profile
                trailingAction = NativeTopBarButtonAction.CreatePost
                configureIconButton(leadingButton, NativeTopBarButtons.profile())
                configureIconButton(trailingIconButton, NativeTopBarButtons.createPost())
            }

            is NativeTopBarModel.Title -> {
                preferLightForeground = model.preferLightForeground
                titleLabel.hidden = false
                titleLabel.text = model.title

                subtitleLabel.text = model.subtitle
                subtitleLabel.hidden = model.subtitle.isNullOrBlank()

                leadingAction = model.leadingButton.action
                configureIconButton(leadingButton, model.leadingButton)

                val trailing = model.trailingButton
                if (trailing == null) {
                    trailingAction = null
                } else if (trailing.style == NativeTopBarButtonStyle.Text) {
                    trailingAction = trailing.action
                    configureTextButton(trailingTextButton, trailing)
                } else {
                    trailingAction = trailing.action
                    configureIconButton(trailingIconButton, trailing)
                }
            }

            is NativeTopBarModel.Search -> {
                preferLightForeground = model.preferLightForeground
                leadingAction = model.leadingButton.action
                trailingAction = null

                configureIconButton(leadingButton, model.leadingButton)
                searchField.hidden = false

                if ((searchField.text ?: "") != model.query) {
                    searchField.text = model.query
                }
                searchField.placeholder = model.placeholder
                applySearchFieldPalette()
            }
        }

        applyLabelPalette()
        applySeparatorPalette()
        setNeedsLayout()

        if (previousModel is NativeTopBarModel.Search && model !is NativeTopBarModel.Search) {
            searchField.resignFirstResponder()
        }
        if (previousModel !is NativeTopBarModel.Search && model is NativeTopBarModel.Search) {
            searchField.becomeFirstResponder()
        }
    }

    fun updateTheme(isDarkTheme: Boolean) {
        this.isDarkTheme = isDarkTheme
        updateModel(currentModel)
    }

    private fun setAllElementsHidden() {
        leadingButton.hidden = true
        trailingIconButton.hidden = true
        trailingTextButton.hidden = true
        titleLabel.hidden = true
        subtitleLabel.hidden = true
        searchField.hidden = true
    }

    private fun layoutHome(
        width: Double,
        contentTop: Double,
        contentHeight: Double,
        sidePadding: Double
    ) {
        val buttonSize = 40.0
        val buttonY = contentTop + (contentHeight - buttonSize) / 2.0

        leadingButton.setFrame(CGRectMake(sidePadding, buttonY, buttonSize, buttonSize))
        trailingIconButton.setFrame(
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
    }

    private fun layoutTitle(
        width: Double,
        contentTop: Double,
        contentHeight: Double,
        sidePadding: Double
    ) {
        val buttonSize = 40.0
        val buttonY = contentTop + (contentHeight - buttonSize) / 2.0
        val horizontalGap = 10.0

        val hasLeading = !leadingButton.hidden
        if (hasLeading) {
            leadingButton.setFrame(CGRectMake(sidePadding, buttonY, buttonSize, buttonSize))
        }

        val trailingWidth = when {
            !trailingIconButton.hidden -> buttonSize
            !trailingTextButton.hidden -> {
                val intrinsicWidth = trailingTextButton.intrinsicContentSize.useContents { width }
                (intrinsicWidth + 24.0).coerceAtLeast(56.0)
            }

            else -> 0.0
        }

        if (!trailingIconButton.hidden) {
            trailingIconButton.setFrame(
                CGRectMake(width - sidePadding - buttonSize, buttonY, buttonSize, buttonSize)
            )
        }

        if (!trailingTextButton.hidden) {
            val textHeight = 32.0
            val textY = contentTop + (contentHeight - textHeight) / 2.0
            trailingTextButton.setFrame(
                CGRectMake(width - sidePadding - trailingWidth, textY, trailingWidth, textHeight)
            )
        }

        val titleStart = sidePadding + if (hasLeading) buttonSize + horizontalGap else 0.0
        val titleEnd = width - sidePadding - if (trailingWidth > 0.0) {
            trailingWidth + horizontalGap
        } else {
            0.0
        }
        val titleWidth = (titleEnd - titleStart).coerceAtLeast(0.0)

        if (subtitleLabel.hidden) {
            titleLabel.setFrame(
                CGRectMake(
                    titleStart,
                    contentTop,
                    titleWidth,
                    contentHeight
                )
            )
            return
        }

        val titleHeight = 22.0
        val subtitleHeight = 16.0
        val spacing = 2.0
        val totalHeight = titleHeight + spacing + subtitleHeight
        val stackedTop = contentTop + (contentHeight - totalHeight) / 2.0

        titleLabel.setFrame(
            CGRectMake(
                titleStart,
                stackedTop,
                titleWidth,
                titleHeight
            )
        )

        subtitleLabel.setFrame(
            CGRectMake(
                titleStart,
                stackedTop + titleHeight + spacing,
                titleWidth,
                subtitleHeight
            )
        )
    }

    private fun layoutSearch(
        width: Double,
        contentTop: Double,
        contentHeight: Double,
        sidePadding: Double
    ) {
        val buttonSize = 40.0
        val buttonY = contentTop + (contentHeight - buttonSize) / 2.0

        leadingButton.setFrame(CGRectMake(sidePadding, buttonY, buttonSize, buttonSize))

        val searchStart = sidePadding + buttonSize + 10.0
        val searchWidth = (width - searchStart - sidePadding).coerceAtLeast(80.0)
        val searchHeight = 44.0
        val searchY = contentTop + (contentHeight - searchHeight) / 2.0

        searchField.setFrame(
            CGRectMake(
                searchStart,
                searchY,
                searchWidth,
                searchHeight
            )
        )
    }

    private fun configureIconButton(button: UIButton, config: NativeTopBarButtonConfig) {
        button.hidden = false
        button.enabled = config.enabled
        button.layer.cornerRadius = 20.0
        button.clipsToBounds = true

        iconNameFor(config.style)?.let { symbol ->
            button.setImage(UIImage.systemImageNamed(symbol), UIControlStateNormal)
        }

        when (config.style) {
            NativeTopBarButtonStyle.Profile -> applySecondaryButtonPalette(button)
            else -> applyPrimaryIconButtonPalette(button)
        }

        button.alpha = if (config.enabled) 1.0 else 0.45
    }

    private fun configureTextButton(button: UIButton, config: NativeTopBarButtonConfig) {
        button.hidden = false
        button.enabled = config.enabled
        button.setTitle(config.text, UIControlStateNormal)
        button.titleLabel?.font = UIFont.boldSystemFontOfSize(15.0)

        val useDarkPalette = useDarkPalette()
        val background = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.92)
        } else {
            UIColor.blackColor
        }
        val foreground = if (useDarkPalette) {
            UIColor.blackColor
        } else {
            UIColor.whiteColor
        }

        button.backgroundColor = background
        button.setTitleColor(foreground, UIControlStateNormal)
        button.alpha = if (config.enabled) 1.0 else 0.45
    }

    private fun applyPrimaryIconButtonPalette(button: UIButton) {
        val useDarkPalette = useDarkPalette()
        val background = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.92)
        } else {
            UIColor.blackColor
        }
        val foreground = if (useDarkPalette) {
            UIColor.blackColor
        } else {
            UIColor.whiteColor
        }

        button.backgroundColor = background
        button.tintColor = foreground
    }

    private fun applySecondaryButtonPalette(button: UIButton) {
        val useDarkPalette = useDarkPalette()
        val background = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.12)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.06)
        }
        val foreground = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.9)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.8)
        }

        button.backgroundColor = background
        button.tintColor = foreground
    }

    private fun applySearchFieldPalette() {
        val useDarkPalette = useDarkPalette()
        val container = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.12)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.06)
        }
        val border = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.2)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.12)
        }
        val textColor = if (useDarkPalette) {
            UIColor.whiteColor
        } else {
            UIColor.blackColor
        }

        searchField.backgroundColor = container
        searchField.layer.borderColor = border.CGColor
        searchField.textColor = textColor
        searchField.tintColor = textColor
    }

    private fun applyLabelPalette() {
        val useDarkPalette = useDarkPalette()
        titleLabel.textColor = if (useDarkPalette) {
            UIColor.whiteColor
        } else {
            UIColor.blackColor
        }
        subtitleLabel.textColor = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.72)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.62)
        }
    }

    private fun applySeparatorPalette() {
        val useDarkPalette = useDarkPalette()
        separator.backgroundColor = if (useDarkPalette) {
            UIColor.whiteColor.colorWithAlphaComponent(0.08)
        } else {
            UIColor.blackColor.colorWithAlphaComponent(0.12)
        }
    }

    private fun useDarkPalette(): Boolean = isDarkTheme || preferLightForeground

    private fun iconNameFor(style: NativeTopBarButtonStyle): String? = when (style) {
        NativeTopBarButtonStyle.Back -> "chevron.left"
        NativeTopBarButtonStyle.Close -> "xmark"
        NativeTopBarButtonStyle.Profile -> "person.fill"
        NativeTopBarButtonStyle.Plus -> "plus"
        NativeTopBarButtonStyle.Edit -> "square.and.pencil"
        NativeTopBarButtonStyle.Text -> null
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
