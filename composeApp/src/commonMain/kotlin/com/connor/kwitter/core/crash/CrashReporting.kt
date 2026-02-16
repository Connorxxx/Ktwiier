package com.connor.kwitter.core.crash

import co.touchlab.crashkios.crashlytics.CrashlyticsKotlin
import co.touchlab.crashkios.crashlytics.enableCrashlytics

fun setupCrashReporting() {
    enableCrashlytics()
    installPlatformUnhandledExceptionHook()
}

fun reportNonFatalCrash(throwable: Throwable) {
    CrashlyticsKotlin.sendHandledException(throwable)
}

expect fun installPlatformUnhandledExceptionHook()
