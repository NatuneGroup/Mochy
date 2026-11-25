// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.theme.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val PILL_MOVE_SPRING = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

internal fun DrawScope.drawMochyNavPill(
    rect: Rect,
    color: Color,
    alpha: Float,
    lift: Float,
) {
    val grow = lift * 3.dp.toPx()
    val liftedColor = color.copy(alpha = (color.alpha * (1f + 0.35f * lift)).coerceIn(0f, 1f))
    drawRoundRect(
        color = liftedColor,
        topLeft = Offset(rect.left - grow, rect.top - grow),
        size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
        cornerRadius = CornerRadius(rect.height / 2f + grow),
        alpha = alpha,
    )
}

/**
 * Draws the Mochy selection pill behind the content of this modifier's layout. [selectedTop]
 * and [selectedHeight] are the selected item's bounds in this layout's pixel space; a null
 * [selectedTop] hides the pill. The pill glides between items as the bounds change.
 */
@Composable
fun Modifier.mochyNavPillIndicator(
    selectedTop: Float?,
    selectedHeight: Float,
    color: Color,
): Modifier {
    val reducedMotion = rememberReducedMotion()
    val top = remember { Animatable(selectedTop ?: 0f) }
    val height = remember { Animatable(selectedHeight) }
    LaunchedEffect(selectedTop, selectedHeight) {
        val target = selectedTop ?: return@LaunchedEffect
        if (reducedMotion) {
            top.snapTo(target)
            height.snapTo(selectedHeight)
        } else {
            coroutineScope {
                launch { top.animateTo(target, PILL_MOVE_SPRING) }
                launch { height.animateTo(selectedHeight, PILL_MOVE_SPRING) }
            }
        }
    }
    return drawBehind {
        if (selectedTop == null) return@drawBehind
        drawMochyNavPill(
            rect = Rect(0f, top.value, size.width, top.value + height.value),
            color = color,
            alpha = 1f,
            lift = 0f,
        )
    }
}
