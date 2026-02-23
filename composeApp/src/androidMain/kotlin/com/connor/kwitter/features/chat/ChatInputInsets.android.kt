package com.connor.kwitter.features.chat

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier

internal actual fun Modifier.chatInputKeyboardAwarePadding(): Modifier {
    // Android activity/window already shifts with IME in this screen, so only keep nav bar safe area.
    return navigationBarsPadding()
}
