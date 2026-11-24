// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.collapse
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FlagshipHandle(
    color: Color,
    closing: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var awake by remember { mutableStateOf(true) }
    LaunchedEffect(pressed, closing) {
        awake = true
        if (!pressed && !closing) {
            delay(4_000L)
            awake = false
        }
    }
    val alpha by animateFloatAsState(
        if (awake) 0.6f else 0.15f,
        tween(durationMillis = if (awake) 220 else 900, easing = FastOutSlowInEasing),
        label = "flagshipHandleAlpha",
    )
    val bend by animateFloatAsState(
        if (pressed || closing) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "flagshipHandleBend",
    )
    val description = stringResource(Res.string.collapse)
    val path = remember { Path() }
    Canvas(
        modifier
            .size(150.dp, 28.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onCollapse,
            ).semantics { contentDescription = description },
    ) {
        val span = lerp(47.5.dp.toPx(), 19.5.dp.toPx(), bend)
        val halfDepth = 5.dp.toPx() * bend
        path.reset()
        path.moveTo(center.x - span, center.y - halfDepth)
        path.lineTo(center.x, center.y + halfDepth)
        path.lineTo(center.x + span, center.y - halfDepth)
        drawPath(
            path,
            color.copy(alpha = alpha),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
