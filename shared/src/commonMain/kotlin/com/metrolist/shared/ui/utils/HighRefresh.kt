// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.utils

import androidx.compose.runtime.Composable

/**
 * Asks the display for its top refresh rate while [active] holds. Android 15+ drops a screen that moves with no
 * finger on it (the player, lyrics, the welcome page) to 60 Hz unless something keeps voting for the high rate.
 */
@Composable
expect fun HighRefreshWhile(active: () -> Boolean)
