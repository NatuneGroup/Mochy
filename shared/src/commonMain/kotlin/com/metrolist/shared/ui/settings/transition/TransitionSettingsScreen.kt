/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings.transition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.crossfade_gapless
import com.metrolist.shared.generated.resources.crossfade_gapless_desc
import com.metrolist.shared.generated.resources.mochy_transition_auto
import com.metrolist.shared.generated.resources.mochy_transition_auto_desc
import com.metrolist.shared.generated.resources.mochy_transition_manual
import com.metrolist.shared.generated.resources.mochy_transition_manual_desc
import com.metrolist.shared.generated.resources.mochy_transition_manual_length
import com.metrolist.shared.generated.resources.mochy_transition_music_heading
import com.metrolist.shared.generated.resources.mochy_transition_off
import com.metrolist.shared.generated.resources.mochy_transition_off_desc
import com.metrolist.shared.generated.resources.mochy_transition_seconds
import com.metrolist.shared.settings.AUTO_CROSSFADE_SECONDS
import com.metrolist.shared.settings.CrossfadeStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.audiolab.LabCard
import com.metrolist.shared.ui.audiolab.LabToggleRow
import com.metrolist.shared.ui.settings.LocalSettingsPageHasTopBar
import com.metrolist.shared.ui.settings.LocalSettingsPageTopPadding
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val MANUAL_MIN_SECONDS = 1f
private const val MANUAL_MAX_SECONDS = 20f
private val MaxContentWidth = 720.dp

/**
 * The Transition page: Off, Auto Crossfade (a fixed 5 s blend) and Manual Crossfade (a 1-20 s blend the listener
 * sets). [onAudioChanged] tells the app the audio side changed, the same way the old Playback rows did.
 */
@Composable
internal fun TransitionSettingsScreen(
    onAudioChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = Settings.shared
    // null means crossfade is off, and the remembered style is kept for when it's turned back on
    val selected: CrossfadeStyle? = if (settings.crossfadeEnabled) settings.crossfadeStyle else null
    val manualSeconds = settings.crossfadeDuration.coerceIn(MANUAL_MIN_SECONDS, MANUAL_MAX_SECONDS)

    fun choose(style: CrossfadeStyle) {
        settings.crossfadeStyle = style
        settings.crossfadeEnabled = true
        if (style == CrossfadeStyle.AUTO) settings.crossfadeDuration = AUTO_CROSSFADE_SECONDS
        onAudioChanged()
    }

    // on Mochy's header the top padding is inside the scroll, so content scrolls up under the header
    val headerTop = if (LocalSettingsPageHasTopBar.current) LocalSettingsPageTopPadding.current else 0.dp
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(top = headerTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
            TransitionHeading(stringResource(Res.string.mochy_transition_music_heading))
            LabCard(padding = 10.dp) {
                LabToggleRow(
                    title = stringResource(Res.string.mochy_transition_off),
                    subtitle = stringResource(Res.string.mochy_transition_off_desc),
                    checked = selected == null,
                    // turning "off" off brings back the blend that was picked last
                    onCheckedChange = { on ->
                        if (on) {
                            settings.crossfadeEnabled = false
                            onAudioChanged()
                        } else {
                            choose(settings.crossfadeStyle)
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))

            TransitionModeGrid(selected = selected) { style, opened ->
                val picked = style == selected
                when (style) {
                    CrossfadeStyle.AUTO -> {
                        TransitionModeCard(
                            title = stringResource(Res.string.mochy_transition_auto),
                            selected = picked,
                            opened = opened,
                            onSelect = { choose(style) },
                            picture = { TransitionPicture(CrossfadeStyle.AUTO, overlap = 0.3f, active = picked) },
                        ) {
                            CardNote(stringResource(Res.string.mochy_transition_auto_desc, AUTO_CROSSFADE_SECONDS.toInt()))
                        }
                    }

                    CrossfadeStyle.MANUAL -> {
                        TransitionModeCard(
                            title = stringResource(Res.string.mochy_transition_manual),
                            selected = picked,
                            opened = opened,
                            onSelect = { choose(style) },
                            picture = {
                                TransitionPicture(
                                    CrossfadeStyle.MANUAL,
                                    overlap =
                                        0.08f + 0.42f * (manualSeconds - MANUAL_MIN_SECONDS) / (MANUAL_MAX_SECONDS - MANUAL_MIN_SECONDS),
                                    active = picked,
                                )
                            },
                        ) {
                            CardNote(stringResource(Res.string.mochy_transition_manual_desc))
                            ManualLength(manualSeconds) {
                                settings.crossfadeDuration = it
                                onAudioChanged()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            TransitionCollapsible(selected != null) {
                LabCard(padding = 10.dp) {
                    LabToggleRow(
                        title = stringResource(Res.string.crossfade_gapless),
                        subtitle = stringResource(Res.string.crossfade_gapless_desc),
                        checked = settings.crossfadeGapless,
                        onCheckedChange = {
                            settings.crossfadeGapless = it
                            onAudioChanged()
                        },
                    )
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

// the small line under an open card's picture
@Composable
private fun CardNote(text: String) {
    Text(
        text = text,
        fontFamily = mochyInterFontFamily(),
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = TransitionColors.Secondary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
    )
}

// the manual blend's length, from a quick one-second cut to a long twenty-second wash
@Composable
private fun ManualLength(
    seconds: Float,
    onSecondsChange: (Float) -> Unit,
) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.mochy_transition_manual_length),
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TransitionColors.Primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.mochy_transition_seconds, seconds.roundToInt()),
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TransitionColors.Primary,
            )
        }
        Slider(
            value = seconds,
            onValueChange = { onSecondsChange(it.roundToInt().toFloat()) },
            valueRange = MANUAL_MIN_SECONDS..MANUAL_MAX_SECONDS,
            steps = (MANUAL_MAX_SECONDS - MANUAL_MIN_SECONDS).toInt() - 1,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.mochy_transition_seconds, MANUAL_MIN_SECONDS.toInt()),
                fontFamily = mochyInterFontFamily(),
                fontSize = 11.sp,
                color = TransitionColors.Secondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.mochy_transition_seconds, MANUAL_MAX_SECONDS.toInt()),
                fontFamily = mochyInterFontFamily(),
                fontSize = 11.sp,
                color = TransitionColors.Secondary,
            )
        }
    }
}
