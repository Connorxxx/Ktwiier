package com.connor.kwitter.features.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual fun supportsNativeGlassBars(): Boolean = false

@Composable
actual fun NativeGlassTopBar(
    modifier: Modifier,
    isDarkTheme: Boolean,
    onCreatePostClick: () -> Unit,
    onProfileClick: (() -> Unit)?
) = Unit

@Composable
actual fun NativeGlassBottomBar(
    modifier: Modifier,
    isDarkTheme: Boolean,
    tabLabels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) = Unit
