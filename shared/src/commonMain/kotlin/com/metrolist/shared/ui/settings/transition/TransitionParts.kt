/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

package com.metrolist.shared.ui.settings.transition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.theme.mochyInterFontFamily

// the transition page's own palette, the same values as the Lab cards it sits beside
internal object TransitionColors {
    val Card = Color(0xFF1C1C1E)
    val Stage = Color(0xFF0E0E10)
    val StageLine = Color(0xFF2E2E32)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5C5C63)
}

/** the dark box a live picture sits in, outlined and clipped so its animation repaints only itself */
@Composable
internal fun TransitionStage(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TransitionColors.Stage)
            .border(1.dp, TransitionColors.StageLine, RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}

/** options that fold away while their effect is off */
@Composable
internal fun TransitionCollapsible(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** the soft heading over each group on the transition page */
@Composable
internal fun TransitionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = mochyInterFontFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        color = Color.White.copy(alpha = 0.82f),
        modifier = modifier.padding(start = 4.dp, end = 16.dp, bottom = 10.dp),
    )
}

/** flags when the picture's box is inside the window, so pictures stop moving once they scroll away */
@Composable
internal fun Modifier.trackPictureOnScreen(state: MutableState<Boolean>): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    return onGloballyPositioned { coords ->
        val bounds: Rect = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < windowHeight
        if (visible != state.value) state.value = visible
    }
}
