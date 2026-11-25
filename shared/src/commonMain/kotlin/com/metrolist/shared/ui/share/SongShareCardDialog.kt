/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.preview
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.generated.resources.share_song_card
import com.metrolist.shared.generated.resources.song_card_share_failed
import com.metrolist.shared.generated.resources.song_card_style_cover
import com.metrolist.shared.generated.resources.song_card_style_wide
import com.metrolist.shared.ui.components.Dialog
import com.metrolist.shared.ui.components.ExpressiveFilterChip
import com.metrolist.shared.ui.components.lyrics.prepareLyricsArtwork
import com.metrolist.shared.ui.utils.toImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Previews a song card, lets the user pick its layout, and hands the rendered PNG-sized bitmap
 * to [onExport] (the platform's image share). Closes once the export reports success.
 */
@Composable
fun SongShareCardDialog(
    title: String,
    artist: String,
    artworkUrl: String?,
    onExport: suspend (ImageBitmap) -> Boolean,
    onDismiss: () -> Unit,
) {
    var style by remember { mutableStateOf(SongShareCardStyle.COVER) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var artwork by remember { mutableStateOf<ImageBitmap?>(null) }
    var palette by remember { mutableStateOf<List<Color>>(emptyList()) }
    var loadingArtwork by remember(artworkUrl) { mutableStateOf(artworkUrl != null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val renderMutex = remember { Mutex() }
    val direction = LocalLayoutDirection.current

    LaunchedEffect(artworkUrl) {
        try {
            if (artworkUrl != null) {
                val result =
                    SingletonImageLoader.get(context).execute(
                        ImageRequest
                            .Builder(context)
                            .data(artworkUrl)
                            .size(512)
                            .build(),
                    )
                val image = result.image?.toImageBitmap()
                if (image != null) {
                    artwork = image
                    palette = withContext(Dispatchers.Default) { prepareLyricsArtwork(image).first }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // no cover: the card falls back to its grey placeholder
        } finally {
            loadingArtwork = false
        }
    }

    LaunchedEffect(style, title, artist, artwork, palette, loadingArtwork) {
        if (!loadingArtwork) {
            bitmap =
                withContext(Dispatchers.Default) {
                    renderMutex.withLock {
                        renderSongShareCard(
                            style = style,
                            title = title,
                            artist = artist,
                            cover = artwork,
                            palette = palette,
                            textMeasurer = measurer,
                            layoutDirection = direction,
                        )
                    }
                }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.share_song_card), style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveFilterChip(
                        selected = style == SongShareCardStyle.COVER,
                        enabled = !busy,
                        onClick = { style = SongShareCardStyle.COVER },
                        label = { Text(stringResource(Res.string.song_card_style_cover)) },
                    )
                    ExpressiveFilterChip(
                        selected = style == SongShareCardStyle.WIDE,
                        enabled = !busy,
                        onClick = { style = SongShareCardStyle.WIDE },
                        label = { Text(stringResource(Res.string.song_card_style_wide)) },
                    )
                }
                Box(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(Res.string.preview),
                            modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height.toFloat()),
                        )
                    }
                }
                if (failed) Text(stringResource(Res.string.song_card_share_failed), color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    Button(
                        enabled = !busy && bitmap != null,
                        onClick = {
                            val image = bitmap ?: return@Button
                            busy = true
                            failed = false
                            scope.launch {
                                val shared =
                                    try {
                                        onExport(image)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        false
                                    }
                                busy = false
                                if (shared) onDismiss() else failed = true
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.share))
                    }
                }
            }
        }
    }
}
