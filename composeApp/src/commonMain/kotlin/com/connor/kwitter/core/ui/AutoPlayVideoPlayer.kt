package com.connor.kwitter.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AutoPlayVideoPlayer(
    url: String,
    modifier: Modifier = Modifier
)
