package com.connor.kwitter.core.util

internal expect object PlatformDateFormatter {
    fun formatTime(epochMillis: Long): String
    fun formatWeekdayTime(epochMillis: Long): String
    fun formatMonthDay(epochMillis: Long): String
    fun formatYearMonthDay(epochMillis: Long): String
}
