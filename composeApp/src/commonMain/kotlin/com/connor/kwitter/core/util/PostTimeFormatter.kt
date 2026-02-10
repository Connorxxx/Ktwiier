package com.connor.kwitter.core.util

import androidx.compose.runtime.Composable
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.time_just_now
import kwitter.composeapp.generated.resources.time_minutes_ago
import kwitter.composeapp.generated.resources.time_yesterday_at
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun formatPostTime(epochMillis: Long): String {
    val timeZone = TimeZone.currentSystemDefault()
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val diffMinutes = (nowMs - epochMillis) / 60_000

    val postDateTime = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(timeZone)
    val nowDateTime = Instant.fromEpochMilliseconds(nowMs)
        .toLocalDateTime(timeZone)

    val postDate = postDateTime.date
    val nowDate = nowDateTime.date
    val yesterday = nowDate.minus(1, DateTimeUnit.DAY)

    return when {
        diffMinutes < 1 -> stringResource(Res.string.time_just_now)
        diffMinutes < 60 -> stringResource(Res.string.time_minutes_ago, diffMinutes)
        postDate == nowDate -> PlatformDateFormatter.formatTime(epochMillis)
        postDate == yesterday -> stringResource(
            Res.string.time_yesterday_at,
            PlatformDateFormatter.formatTime(epochMillis)
        )
        postDate > nowDate.minus(7, DateTimeUnit.DAY) -> PlatformDateFormatter.formatWeekdayTime(epochMillis)
        postDateTime.year == nowDateTime.year -> PlatformDateFormatter.formatMonthDay(epochMillis)
        else -> PlatformDateFormatter.formatYearMonthDay(epochMillis)
    }
}
