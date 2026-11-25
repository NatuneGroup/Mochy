// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// the record sits inside its square so its edge never touches the card
const val FLAGSHIP_DISC_DIAMETER = 0.94f
const val FLAGSHIP_DISC_CENTER = 0.5f

// the side padding of the flagship's portrait column, which the full-bleed cover reaches past
internal val FlagshipSidePadding = 32.dp

// widens the square artwork sideways past the column while keeping its height, so the controls below do not move
internal fun Modifier.coverBleed(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val side = constraints.maxWidth
        val height = side.coerceIn(constraints.minHeight, constraints.maxHeight)
        val bleed = amount.roundToPx()
        val placeable = measurable.measure(Constraints.fixed(side + 2 * bleed, height))
        layout(side, height) { placeable.place(-bleed, 0) }
    }

// fades the bottom of the full-bleed artwork into the backdrop behind the controls
fun Modifier.coverFadeToBottom(): Modifier =
    Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(0.62f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
