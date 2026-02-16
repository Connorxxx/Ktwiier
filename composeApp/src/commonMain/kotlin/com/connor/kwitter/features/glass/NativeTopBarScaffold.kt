package com.connor.kwitter.features.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val NativeTopBarPlaceholderHeight = 116.dp

@Composable
fun rememberNativeTopBarController(): NativeTopBarController? = remember { getNativeTopBarController() }

@Composable
fun NativeTopBarPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NativeTopBarPlaceholderHeight)
    )
}

@Composable
fun NativeTopBarSlot(
    nativeTopBarController: NativeTopBarController?,
    composeTopBar: @Composable () -> Unit
) {
    if (nativeTopBarController == null) {
        composeTopBar()
    } else {
        NativeTopBarPlaceholder()
    }
}
