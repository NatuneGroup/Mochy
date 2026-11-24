package com.metrolist.shared.ui.player.mochy

import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.util.Date

@Composable
internal actual fun bedsideClockText(epochMillis: Long): BedsideClockText {
    val date = Date(epochMillis)
    return BedsideClockText(
        time = DateFormat.getTimeInstance(DateFormat.SHORT).format(date),
        date = DateFormat.getDateInstance(DateFormat.FULL).format(date),
    )
}
