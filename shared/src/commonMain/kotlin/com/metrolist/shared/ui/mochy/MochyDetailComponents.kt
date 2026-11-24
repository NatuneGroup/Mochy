// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.theme.rememberArtworkSeedColor
import com.metrolist.shared.ui.theme.rememberReducedMotion
import com.metrolist.shared.ui.theme.toHsv

private const val DETAIL_GLOW_DURATION_MS = 550

@Composable
internal fun MochyDetailCoverBackdrop(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    topDepth: Dp = 360.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val seedColor = rememberArtworkSeedColor(thumbnailUrl)
    val targetColor = seedColor?.toDetailTone(isDark) ?: Color.Transparent
    val reducedMotion = rememberReducedMotion()
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else DETAIL_GLOW_DURATION_MS),
        label = "mochyDetailBackdropColor",
    )
    val peakAlpha = if (isDark) 0.44f else 0.24f

    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(topDepth)
                .drawWithCache {
                    if (animatedColor.alpha == 0f || size.width <= 0f || size.height <= 0f) {
                        return@drawWithCache onDrawBehind {}
                    }
                    val radial =
                        Brush.radialGradient(
                            0f to animatedColor.copy(alpha = peakAlpha),
                            0.55f to animatedColor.copy(alpha = peakAlpha * 0.48f),
                            1f to Color.Transparent,
                            center = Offset(size.width * 0.5f, size.height * 0.22f),
                            radius = maxOf(size.width, size.height) * 0.85f,
                        )
                    val verticalFade =
                        Brush.verticalGradient(
                            0f to animatedColor.copy(alpha = peakAlpha * 0.6f),
                            0.45f to animatedColor.copy(alpha = peakAlpha * 0.22f),
                            1f to Color.Transparent,
                        )
                    onDrawBehind {
                        drawRect(brush = verticalFade)
                        drawRect(brush = radial)
                    }
                },
    )
}

@Composable
internal fun MochyCollapsingTopBar(
    visible: Boolean,
    title: String,
    thumbnailUrl: String?,
    hasMoreOptions: Boolean,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(if (reducedMotion) 0 else 180)),
        exit = fadeOut(tween(if (reducedMotion) 0 else 180)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)),
        ) {
            MochyCollapsedTopBarContent(
                title = title,
                thumbnailUrl = thumbnailUrl,
                hasMoreOptions = hasMoreOptions,
                fallbackIcon = fallbackIcon,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

@Composable
internal fun MochyCollapsedTopBarContent(
    title: String,
    thumbnailUrl: String?,
    hasMoreOptions: Boolean,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = if (hasMoreOptions) 56.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!thumbnailUrl.isNullOrBlank() || fallbackIcon != null) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.75.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        url = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (fallbackIcon != null) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = (-0.02).em,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Color.toDetailTone(isDark: Boolean): Color {
    val hsv = toHsv()
    val saturation = hsv[1].coerceAtMost(if (isDark) 0.58f else 0.45f)
    val value = if (isDark) hsv[2].coerceIn(0.22f, 0.42f) else hsv[2].coerceIn(0.60f, 0.85f)
    return Color.hsv(hsv[0], saturation, value)
}
