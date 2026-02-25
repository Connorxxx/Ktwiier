package com.connor.kwitter.features.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal actual fun Modifier.chatInputKeyboardAwarePadding(): Modifier {
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardGap = if (imeBottom > 0) 6.dp else 0.dp

    // Android window resize already handles IME movement in this screen.
    // Keep only navigation bar inset so IME does not get applied twice.
    return navigationBarsPadding()
        .padding(bottom = keyboardGap)
}
