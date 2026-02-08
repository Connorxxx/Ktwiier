package com.connor.kwitter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.connor.kwitter.core.media.addPlatformComponents
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.features.main.MainScreen

@Composable
@Preview
fun App(isDarkTheme: Boolean? = null) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                addPlatformComponents()
            }
            .build()
    }

    KwitterTheme(darkTheme = isDarkTheme ?: isSystemInDarkTheme()) {
        MainScreen()
    }
}
