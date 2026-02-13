package com.connor.kwitter.features.glass

import kotlinx.coroutines.flow.Flow

sealed interface NativeTopBarAction {
    data object CreatePost : NativeTopBarAction
    data object Profile : NativeTopBarAction
}

interface NativeTopBarController {
    val actionEvents: Flow<NativeTopBarAction>
    fun setTopBarVisible(visible: Boolean)
}

expect fun getNativeTopBarController(): NativeTopBarController?
