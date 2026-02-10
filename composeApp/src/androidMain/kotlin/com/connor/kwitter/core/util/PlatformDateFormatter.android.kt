package com.connor.kwitter.core.util

import android.icu.text.DateFormat
import java.util.Date
import java.util.Locale

internal actual object PlatformDateFormatter {

    private val locale: Locale get() = Locale.getDefault()

    actual fun formatTime(epochMillis: Long): String =
        DateFormat.getInstanceForSkeleton("jm", locale).format(Date(epochMillis))

    actual fun formatWeekdayTime(epochMillis: Long): String =
        DateFormat.getInstanceForSkeleton("Ejm", locale).format(Date(epochMillis))

    actual fun formatMonthDay(epochMillis: Long): String =
        DateFormat.getInstanceForSkeleton("MMMd", locale).format(Date(epochMillis))

    actual fun formatYearMonthDay(epochMillis: Long): String =
        DateFormat.getInstanceForSkeleton("yMMMd", locale).format(Date(epochMillis))
}
