package com.connor.kwitter.features.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.chatInputKeyboardAwarePadding(): Modifier {
    return windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .imePadding()
}
