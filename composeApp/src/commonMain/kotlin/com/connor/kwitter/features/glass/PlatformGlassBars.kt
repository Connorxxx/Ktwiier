package com.connor.kwitter.features.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun supportsNativeGlassBars(): Boolean

@Composable
expect fun NativeGlassBottomBar(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    tabLabels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
)
