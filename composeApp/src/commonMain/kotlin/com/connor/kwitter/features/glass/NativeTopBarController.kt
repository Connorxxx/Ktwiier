package com.connor.kwitter.features.glass

import kotlinx.coroutines.flow.Flow

sealed interface NativeTopBarAction {
    data class ButtonClicked(val action: NativeTopBarButtonAction) : NativeTopBarAction
    data class SearchQueryChanged(val query: String) : NativeTopBarAction
    data object SearchSubmitted : NativeTopBarAction
}

enum class NativeTopBarButtonAction {
    Back,
    Close,
    CreatePost,
    Profile,
    Edit,
    Save
}

enum class NativeTopBarButtonStyle {
    Back,
    Close,
    Profile,
    Plus,
    Edit,
    Text
}

data class NativeTopBarButtonConfig(
    val action: NativeTopBarButtonAction,
    val style: NativeTopBarButtonStyle,
    val text: String = "",
    val enabled: Boolean = true
)

object NativeTopBarButtons {
    fun back(enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.Back,
            style = NativeTopBarButtonStyle.Back,
            enabled = enabled
        )

    fun close(enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.Close,
            style = NativeTopBarButtonStyle.Close,
            enabled = enabled
        )

    fun createPost(enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.CreatePost,
            style = NativeTopBarButtonStyle.Plus,
            enabled = enabled
        )

    fun profile(enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.Profile,
            style = NativeTopBarButtonStyle.Profile,
            enabled = enabled
        )

    fun edit(enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.Edit,
            style = NativeTopBarButtonStyle.Edit,
            enabled = enabled
        )

    fun save(label: String, enabled: Boolean = true): NativeTopBarButtonConfig =
        NativeTopBarButtonConfig(
            action = NativeTopBarButtonAction.Save,
            style = NativeTopBarButtonStyle.Text,
            text = label,
            enabled = enabled
        )
}

sealed interface NativeTopBarModel {
    data object Hidden : NativeTopBarModel

    data object HomeInteractive : NativeTopBarModel

    data class Title(
        val title: String,
        val subtitle: String? = null,
        val leadingButton: NativeTopBarButtonConfig,
        val trailingButton: NativeTopBarButtonConfig? = null,
        val preferLightForeground: Boolean = false
    ) : NativeTopBarModel

    data class Search(
        val query: String,
        val placeholder: String,
        val leadingButton: NativeTopBarButtonConfig,
        val preferLightForeground: Boolean = false
    ) : NativeTopBarModel
}

interface NativeTopBarController {
    val actionEvents: Flow<NativeTopBarAction>
    fun setModel(model: NativeTopBarModel)
}

expect fun getNativeTopBarController(): NativeTopBarController?
