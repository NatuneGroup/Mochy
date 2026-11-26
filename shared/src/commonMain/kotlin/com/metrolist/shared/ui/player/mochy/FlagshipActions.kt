// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.mochy_expand_less
import com.metrolist.shared.generated.resources.mochy_heart
import com.metrolist.shared.generated.resources.mochy_heart_outline
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.ui.mochy.LikeBurstState
import com.metrolist.shared.ui.mochy.LikeSlot
import com.metrolist.shared.ui.mochy.likeButtonTarget
import com.metrolist.shared.ui.navigation.navigationPopupSource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FlagshipActions(
    liked: Boolean,
    menuOpen: Boolean,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    contentColor: Color = Color.White,
    likeBurstState: LikeBurstState? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        FlagshipActionButton(
            icon = if (liked) Res.drawable.mochy_heart else Res.drawable.mochy_heart_outline,
            description = stringResource(if (liked) Res.string.unlike else Res.string.action_like),
            tint = contentColor.copy(alpha = if (liked) 1f else 0.85f),
            background = contentColor.copy(alpha = if (liked) 0.30f else 0.15f),
            onClick = onLike,
            modifier = Modifier.likeButtonTarget(likeBurstState, LikeSlot.PLAYER),
        )
        val rotation by animateFloatAsState(
            if (menuOpen) 180f else 0f,
            spring(dampingRatio = 0.55f, stiffness = 380f),
            label = "flagshipChevron",
        )
        val surface by animateColorAsState(
            contentColor.copy(alpha = if (menuOpen) 0.32f else 0.15f),
            tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "flagshipChevronSurface",
        )
        FlagshipActionButton(
            icon = Res.drawable.mochy_expand_less,
            description = stringResource(Res.string.more_options),
            tint = contentColor.copy(alpha = if (menuOpen) 1f else 0.90f),
            background = surface,
            onClick = onMenu,
            modifier = Modifier.navigationPopupSource(),
            iconModifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * Vertically stacked like + chevron actions used at the bottom-end corner of Mochy's Flagship
 * landscape player.
 */
@Composable
internal fun FlagshipCoverButtons(
    liked: Boolean,
    menuOpen: Boolean,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    likeBurstState: LikeBurstState? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlagshipActionButton(
            icon = if (liked) Res.drawable.mochy_heart else Res.drawable.mochy_heart_outline,
            description = stringResource(if (liked) Res.string.unlike else Res.string.action_like),
            tint = contentColor.copy(alpha = if (liked) 1f else 0.85f),
            background = contentColor.copy(alpha = if (liked) 0.30f else 0.15f),
            onClick = onLike,
            modifier = Modifier.likeButtonTarget(likeBurstState, LikeSlot.PLAYER),
        )
        val rotation by animateFloatAsState(
            if (menuOpen) 180f else 0f,
            spring(dampingRatio = 0.55f, stiffness = 380f),
            label = "flagshipLandscapeChevron",
        )
        val surface by animateColorAsState(
            contentColor.copy(alpha = if (menuOpen) 0.32f else 0.15f),
            tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "flagshipLandscapeChevronSurface",
        )
        FlagshipActionButton(
            icon = Res.drawable.mochy_expand_less,
            description = stringResource(Res.string.more_options),
            tint = contentColor.copy(alpha = if (menuOpen) 1f else 0.90f),
            background = surface,
            onClick = onMenu,
            modifier = Modifier.navigationPopupSource(),
            iconModifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

// one of the phone's cover action buttons, for the desktop fullscreen player
@Composable
fun FlagshipCoverActionButton(
    icon: DrawableResource,
    description: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlagshipActionButton(icon = icon, description = description, tint = tint, background = background, onClick = onClick, modifier = modifier)
}

@Composable
internal fun FlagshipActionButton(
    icon: DrawableResource,
    description: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = flagshipPressScale(interaction)
    Box(
        modifier =
            modifier
                .size(34.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scaleX
                    }.clip(CircleShape)
                    .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                colorFilter = ColorFilter.tint(tint),
                modifier = iconModifier.size(18.dp),
            )
        }
    }
}
