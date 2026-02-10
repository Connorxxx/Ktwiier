package com.connor.kwitter.core.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeIntervalSince1970

internal actual object PlatformDateFormatter {

    private fun format(epochMillis: Long, template: String): String {
        val formatter = NSDateFormatter().apply {
            setLocalizedDateFormatFromTemplate(template)
        }
        val secondsSince1970 = epochMillis.toDouble() / 1000
        val date = NSDate(timeIntervalSinceReferenceDate = secondsSince1970 - NSTimeIntervalSince1970)
        return formatter.stringFromDate(date)
    }

    actual fun formatTime(epochMillis: Long): String = format(epochMillis, "jm")

    actual fun formatWeekdayTime(epochMillis: Long): String = format(epochMillis, "Ejm")

    actual fun formatMonthDay(epochMillis: Long): String = format(epochMillis, "MMMd")

    actual fun formatYearMonthDay(epochMillis: Long): String = format(epochMillis, "yMMMd")
}
