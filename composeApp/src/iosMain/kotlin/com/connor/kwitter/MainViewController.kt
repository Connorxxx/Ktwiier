package com.connor.kwitter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.core.crash.setupCrashReporting
import com.connor.kwitter.di.initKoin
import com.connor.kwitter.features.glass.NativeTabBridge
import com.connor.kwitter.features.glass.NativeTopBarBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.UITabBar
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.main_tab_home
import kwitter.composeapp.generated.resources.main_tab_messages
import kwitter.composeapp.generated.resources.main_tab_search
import kwitter.composeapp.generated.resources.main_tab_settings

private val isDarkTheme = mutableStateOf(false)
private val localizationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun setupPlatformCrashReporting() {
    setupCrashReporting()
}

fun MainViewController(isDarkTheme: Boolean): UIViewController {
    initKoin()
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
    NativeTopBarBridge.setDarkTheme(isDarkTheme)
    return ComposeUIViewController {
        App(isDarkTheme = com.connor.kwitter.isDarkTheme.value)
    }
}

fun updateDarkTheme(viewController: UIViewController, isDarkTheme: Boolean) {
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
    NativeTopBarBridge.setDarkTheme(isDarkTheme)
}

// Called from Swift after native UITabBar is configured
fun registerNativeTabBar(tabBar: UITabBar) {
    NativeTabBridge.configure(tabBar)
}

// Called from Swift on every layout pass after UITabBar height is measured
fun updateNativeTabBarHeight(height: Double) {
    NativeTabBridge.updateTabBarHeight(height)
}

fun createNativeTopBarView(): UIView = NativeTopBarBridge.createTopBarView()

fun loadLocalizedMainTabTitles(
    onLoaded: (home: String, messages: String, search: String, settings: String) -> Unit
) {
    localizationScope.launch {
        val titles = withContext(Dispatchers.Default) {
            MainTabTitles(
                home = runCatching { getString(Res.string.main_tab_home) }.getOrDefault("Home"),
                messages = runCatching { getString(Res.string.main_tab_messages) }
                    .getOrDefault("Messages"),
                search = runCatching { getString(Res.string.main_tab_search) }.getOrDefault("Search"),
                settings = runCatching { getString(Res.string.main_tab_settings) }
                    .getOrDefault("Settings")
            )
        }
        onLoaded(titles.home, titles.messages, titles.search, titles.settings)
    }
}

private data class MainTabTitles(
    val home: String,
    val messages: String,
    val search: String,
    val settings: String
)

// Called from Swift delegate when a native tab is selected
fun onNativeTabSelected(index: Int) {
    NativeTabBridge.onNativeTabSelected(index)
}
