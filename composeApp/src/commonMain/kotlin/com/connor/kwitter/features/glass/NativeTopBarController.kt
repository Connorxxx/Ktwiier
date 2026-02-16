package com.connor.kwitter.features.glass

import kotlinx.coroutines.flow.Flow

sealed interface NativeTopBarAction {
    data object CreatePost : NativeTopBarAction
    data object Profile : NativeTopBarAction
}

enum class NativeTopBarMode {
    Hidden,
    HomeInteractive
}

interface NativeTopBarController {
    val actionEvents: Flow<NativeTopBarAction>
    fun setTopBarMode(mode: NativeTopBarMode)
}

expect fun getNativeTopBarController(): NativeTopBarController?
