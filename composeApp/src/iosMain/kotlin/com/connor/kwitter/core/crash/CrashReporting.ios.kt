package com.connor.kwitter.core.crash

import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook

actual fun installPlatformUnhandledExceptionHook() {
    setCrashlyticsUnhandledExceptionHook()
}
