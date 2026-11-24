package com.metrolist.shared.ui.player.mochy

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Date

@Composable
internal actual fun bedsideClockText(epochMillis: Long): BedsideClockText {
    val context = LocalContext.current
    val date = Date(epochMillis)
    val is24Hour = DateFormat.is24HourFormat(context)
    return BedsideClockText(
        time = DateFormat.getTimeFormat(context).format(date),
        date = DateFormat.getLongDateFormat(context).format(date),
        is24Hour = is24Hour,
        period = if (is24Hour) null else DateFormat.format("a", date).toString(),
    )
}
