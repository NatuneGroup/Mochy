package com.metrolist.shared.ui.player.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun bedsideClockText(epochMillis: Long): BedsideClockText {
    val timeFormatter = remember { NSDateFormatter().apply { timeStyle = NSDateFormatterShortStyle } }
    val dateFormatter = remember { NSDateFormatter().apply { dateStyle = NSDateFormatterFullStyle } }
    val periodFormatter = remember { NSDateFormatter().apply { dateFormat = "a" } }
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1_000.0)
    val is24Hour = !timeFormatter.dateFormat.contains('a')
    return BedsideClockText(
        time = timeFormatter.stringFromDate(date),
        date = dateFormatter.stringFromDate(date),
        is24Hour = is24Hour,
        period = if (is24Hour) null else periodFormatter.stringFromDate(date),
    )
}
