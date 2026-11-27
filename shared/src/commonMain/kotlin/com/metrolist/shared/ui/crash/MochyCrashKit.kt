// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// The crash pages in Mochy's look: its black page with the round header button, the update prompt's 56dp pill
// buttons and 18dp cards on the menu row surface, and the Mochy mark in its round tile.

package com.metrolist.shared.ui.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.painterResource

val MochyCrashPageColor = Color.Black
val MochyCrashCardColor = Color(0xFF161619)
val MochyCrashMutedText = Color(0xFF9A9A9A)
val MochyCrashErrorText = Color(0xFFE0736B)
private val HeaderButtonColor = Color(0xFF2C2C2E)
private val ProgressFill = Color(0xFF444444)

/** A crash page: the round header button, then the page's big title, then its content. */
@Composable
fun MochyCrashPage(
    title: String,
    headerIcon: ImageVector,
    headerIconDescription: String,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
    headerEnd: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MochyCrashPageColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            MochyCrashRoundButton(headerIcon, headerIconDescription, onHeaderClick)
            Spacer(Modifier.weight(1f))
            headerEnd?.invoke()
        }
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        )
        content()
    }
}

@Composable
fun MochyCrashRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HeaderButtonColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** The Mochy mark in its round tile, the way the update prompt opens. */
@Composable
fun MochyCrashMark() {
    Box(
        modifier = Modifier.size(96.dp).clip(CircleShape).background(MochyCrashCardColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.mochy_mark),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(52.dp),
        )
    }
}

/** The update prompt's button: a full-width 56dp pill, filled from the left while [progress] runs. */
@Composable
fun MochyPromptButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    progress: Float = 0f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(MochyCrashCardColor)
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(ProgressFill),
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
            fontFamily = mochyInterFontFamily(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A card on the menu row surface, as the update prompt holds its changelog. */
@Composable
fun MochyCrashCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MochyCrashCardColor)
            .padding(16.dp),
        content = content,
    )
}

/** Muted body text under a crash page's title. */
@Composable
fun MochyCrashBodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MochyCrashMutedText,
) {
    Text(
        text = text,
        color = color,
        fontFamily = mochyInterFontFamily(),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier,
    )
}

/** A crash log's own text, small and monospaced so stack frames line up. */
@Composable
fun MochyCrashLogText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = modifier,
    )
}
