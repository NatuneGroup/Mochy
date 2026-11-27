// Ported from Natune (github.com/NatuneGroup/Natune), ArchiveTune by Rukamori. Licensed under GPL-3.0.

package com.metrolist.shared.ui.lyrics.natune

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lyrics_not_found
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.ui.mochy.lyrics.MochyLyricsScreen
import org.jetbrains.compose.resources.stringResource

// the karaoke view has no iOS simulator build, so iOS draws Mochy's own lyrics list in the sheet
@Composable
actual fun NatuneLyricsPane(
    lines: List<LyricsEntry>,
    isLoading: Boolean,
    positionProvider: () -> Long,
    onSeekToLine: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier,
) {
    MochyLyricsScreen(
        lines = lines,
        isLoading = isLoading,
        positionProvider = positionProvider,
        onSeek = onSeekToLine,
        accentColor = textColor,
        noLyricsText = stringResource(Res.string.lyrics_not_found),
        modifier = modifier,
    )
}
