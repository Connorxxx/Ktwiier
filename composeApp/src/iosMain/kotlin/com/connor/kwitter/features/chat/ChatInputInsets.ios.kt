package com.connor.kwitter.features.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.chatInputKeyboardAwarePadding(): Modifier {
    // Keep the home-indicator safe area when the keyboard is closed, but switch to the
    // larger IME inset when it opens instead of stacking both bottom paddings.
    return windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
    )
}
