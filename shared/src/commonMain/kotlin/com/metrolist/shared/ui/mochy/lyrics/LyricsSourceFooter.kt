// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_lyrics_source
import com.metrolist.shared.generated.resources.mochy_lyrics_submitted_by
import com.metrolist.shared.generated.resources.mochy_lyrics_swap_vert
import com.metrolist.shared.generated.resources.mochy_lyrics_switch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val LabelColor = Color.White.copy(alpha = 0.55f)
private val ValueColor = Color.White.copy(alpha = 0.38f)

private val FooterTextStyle = TextStyle(fontSize = 17.sp, letterSpacing = (-0.01).em, color = ValueColor)

private val ButtonTextStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

/**
 * The end of the lyrics page: where these lyrics came from, who curated them when the source credits a
 * person, and a button to pick another source.
 */
@Composable
internal fun LyricsSourceFooter(
    providerName: String,
    providerUrl: String?,
    curator: String?,
    curatorUrl: String?,
    onSwitchSource: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledText(
                label = stringResource(Res.string.mochy_lyrics_source),
                value = providerName,
                onValueClick = providerUrl?.let { url -> { runCatching { uriHandler.openUri(url) } } },
            )
            if (onSwitchSource != null) {
                Spacer(modifier = Modifier.width(10.dp))
                SwitchSourceButton(onClick = onSwitchSource)
            }
        }
        if (curator != null) {
            LabeledText(
                label = stringResource(Res.string.mochy_lyrics_submitted_by),
                value = curator,
                onValueClick = curatorUrl?.let { url -> { runCatching { uriHandler.openUri(url) } } },
            )
        }
    }
}

@Composable
private fun LabeledText(
    label: String,
    value: String?,
    onValueClick: (() -> Unit)? = null,
) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = LabelColor)) { append(label) }
                if (value == null) return@buildAnnotatedString
                append(" ")
                // an underline tells people the source name opens its website
                if (onValueClick != null) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(value) }
                } else {
                    append(value)
                }
            },
        style = FooterTextStyle,
        maxLines = 1,
        modifier = if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier,
    )
}

@Composable
private fun SwitchSourceButton(onClick: () -> Unit) {
    FooterButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        background = Color.White.copy(alpha = 0.08f),
    ) {
        Icon(
            painter = painterResource(Res.drawable.mochy_lyrics_swap_vert),
            contentDescription = null,
            tint = LabelColor,
            modifier = Modifier.size(15.dp),
        )
        Text(text = stringResource(Res.string.mochy_lyrics_switch), style = ButtonTextStyle.copy(color = LabelColor))
    }
}

/** Shrinks a little while held, so a tap feels like pressing something. */
@Composable
private fun FooterButton(
    onClick: () -> Unit,
    shape: Shape,
    background: Color,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "footerButtonScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(background)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        content()
    }
}
