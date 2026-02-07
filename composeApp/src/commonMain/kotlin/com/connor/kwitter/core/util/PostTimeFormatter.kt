package com.connor.kwitter.core.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun formatPostTime(epochMillis: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(dateTime.year)
        append('-')
        append(dateTime.month.number.toString().padStart(2, '0'))
        append('-')
        append(dateTime.day.toString().padStart(2, '0'))
        append(' ')
        append(dateTime.hour.toString().padStart(2, '0'))
        append(':')
        append(dateTime.minute.toString().padStart(2, '0'))
    }
}
