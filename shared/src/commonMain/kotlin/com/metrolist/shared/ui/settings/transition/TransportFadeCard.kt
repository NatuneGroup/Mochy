/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings.transition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_transition_fade_desc
import com.metrolist.shared.generated.resources.mochy_transition_fade_seconds
import com.metrolist.shared.generated.resources.mochy_transition_fade_title
import com.metrolist.shared.generated.resources.mochy_transition_manual_length
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.audiolab.LabToggleRow
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The pause, play, forward and backward fade: the curve picture, the enable switch and its length. Reads and writes
 * the existing transportFade settings. Shown inside the Playback sound group, so it adds no outer card of its own.
 */
@Composable
internal fun TransportFadeCard(onSettingValueChanged: () -> Unit) {
    val settings = Settings.shared
    val enabled = settings.transportFadeEnabled
    val seconds = settings.transportFadeSeconds.coerceIn(TRANSPORT_FADE_MIN_SECONDS, TRANSPORT_FADE_MAX_SECONDS)

    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        TransitionStage(height = 150.dp) {
            TransportFadePicture(enabled = enabled, seconds = seconds)
        }
        Spacer(Modifier.height(10.dp))
        LabToggleRow(
            title = stringResource(Res.string.mochy_transition_fade_title),
            subtitle = stringResource(Res.string.mochy_transition_fade_desc),
            checked = enabled,
            onCheckedChange = {
                settings.transportFadeEnabled = it
                onSettingValueChanged()
            },
        )
        TransitionCollapsible(enabled) {
            Box(Modifier.padding(horizontal = 6.dp)) {
                TransitionSlider(
                    label = stringResource(Res.string.mochy_transition_manual_length),
                    valueText = transportSecondsText(seconds),
                    value = seconds,
                    onValueChange = {
                        // half-second steps, the same as the stored setting has always been
                        settings.transportFadeSeconds = (it * 2f).roundToInt() / 2f
                        onSettingValueChanged()
                    },
                    valueRange = TRANSPORT_FADE_MIN_SECONDS..TRANSPORT_FADE_MAX_SECONDS,
                    steps = ((TRANSPORT_FADE_MAX_SECONDS - TRANSPORT_FADE_MIN_SECONDS) * 2f).toInt() - 1,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(
                    stringResource(Res.string.mochy_transition_fade_seconds, transportSecondsText(TRANSPORT_FADE_MIN_SECONDS)),
                    fontFamily = mochyInterFontFamily(),
                    fontSize = 11.sp,
                    color = TransitionColors.Secondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.mochy_transition_fade_seconds, transportSecondsText(TRANSPORT_FADE_MAX_SECONDS)),
                    fontFamily = mochyInterFontFamily(),
                    fontSize = 11.sp,
                    color = TransitionColors.Secondary,
                )
            }
        }
    }
}

// whole seconds as "2", half seconds as "2.5", without String.format so it stays in common code
private fun transportSecondsText(seconds: Float): String {
    val tenths = (seconds * 10f).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
