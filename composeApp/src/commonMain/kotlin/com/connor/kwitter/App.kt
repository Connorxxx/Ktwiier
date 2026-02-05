package com.connor.kwitter

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.features.main.MainScreen

@Composable
@Preview
fun App() {
    KwitterTheme {
        MainScreen()
    }
}
