// Inspired by the bedside clock in Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

internal data class BedsideClockText(
    val time: String,
    val date: String,
)

@Composable
internal expect fun bedsideClockText(epochMillis: Long): BedsideClockText

@Composable
fun BedsideClock(
    title: String?,
    artist: String?,
    isPlaying: Boolean,
    onTogglePlay: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = Clock.System.now().toEpochMilliseconds()
                delay(60_000L - now.mod(60_000L))
            }
        }
    }
    val clock = bedsideClockText(now)
    val playPauseDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val clockFontSize = with(LocalDensity.current) { minOf(maxWidth / 6, maxHeight / 3, 168.dp).toSp() }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = clock.date,
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = clock.time,
                    color = Color.White,
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontSize = clockFontSize,
                            lineHeight = clockFontSize,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                title?.takeIf(String::isNotBlank)?.let { songTitle ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = songTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            artist?.takeIf(String::isNotBlank)?.let {
                                Text(
                                    text = it,
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (onTogglePlay != null) {
                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier.semantics { contentDescription = playPauseDescription },
                            ) {
                                AnimatedPlayGlyph(
                                    playing = isPlaying,
                                    color = Color.White,
                                    height = 20.dp,
                                )
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = MetrolistIcons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
        }
    }
}
