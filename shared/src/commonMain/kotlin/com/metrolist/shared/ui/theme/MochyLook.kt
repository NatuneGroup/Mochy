// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.metrolist.shared.modules.MochyModuleRegistry
import com.metrolist.shared.settings.MiniPlayerType
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.settings.Settings

class MochyLook internal constructor(
    values: Map<String, String>,
) {
    private val player = values["player"]?.trim()
    private val mini = values["mini"]?.trim()
    private val bar = values["bar"]?.trim()
    internal val font = values["font"]?.trim()?.lowercase()

    val playerType: PlayerType
        get() =
            when {
                player.equals("flagship", ignoreCase = true) -> PlayerType.MOCHY
                player.equals("standard", ignoreCase = true) -> PlayerType.MODERN
                else -> PlayerType.entries.firstOrNull { it.name.equals(player, ignoreCase = true) } ?: Settings.shared.playerType
            }

    val miniPlayerType: MiniPlayerType
        get() = MiniPlayerType.entries.firstOrNull { it.name.equals(mini, ignoreCase = true) } ?: Settings.shared.miniPlayerType

    val slimNavBar: Boolean
        get() =
            when (bar?.lowercase()) {
                "compact", "slim" -> true
                "standard", "floating" -> false
                else -> Settings.shared.slimNavBar
            }

    val floatingNavBar: Boolean
        get() =
            when (bar?.lowercase()) {
                "floating" -> true
                "standard", "compact", "slim" -> false
                else -> Settings.shared.floatingNavBar
            }
}

@Composable
fun rememberMochyLook(): MochyLook {
    val values by MochyModuleRegistry.shared.activeLook.collectAsState()
    return remember(values) { MochyLook(values) }
}
