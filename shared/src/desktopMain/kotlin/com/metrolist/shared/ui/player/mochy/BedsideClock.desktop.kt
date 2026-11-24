package com.metrolist.shared.ui.player.mochy

import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal actual fun bedsideClockText(epochMillis: Long): BedsideClockText {
    val date = Date(epochMillis)
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val is24Hour = (timeFormat as? SimpleDateFormat)?.toPattern()?.contains('a') != true
    return BedsideClockText(
        time = timeFormat.format(date),
        date = DateFormat.getDateInstance(DateFormat.FULL).format(date),
        is24Hour = is24Hour,
        period = if (is24Hour) null else SimpleDateFormat("a").format(date),
    )
}
