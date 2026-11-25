// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_name
import com.metrolist.shared.generated.resources.home
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.supportsNativeBlur
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Mochy's home content inset: the mark, the hero text and the shelf titles all start here. */
val MochyHomeContentInset = 36.dp

/**
 * Mochy's home top bar. Over the hero it shows the brand mark and the profile button. Once the hero scrolls
 * halfway away, the mark blurs and shrinks into a bold "Home" title and a scrim builds up under the bar.
 */
@Composable
fun MochyHomeTopBar(
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isScrolled by remember(scrollProgress) { derivedStateOf { scrollProgress() > 0.5f } }
    val topBarProgress by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "mochyHomeTopBar",
    )
    val blur = supportsNativeBlur()
    val scrimColor = MaterialTheme.colorScheme.background
    val titleColor = MaterialTheme.colorScheme.onBackground
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        // every stop shares one colour, so the fade is built once and only its alpha follows the scroll
                        val fade =
                            Brush.verticalGradient(
                                0f to scrimColor.copy(alpha = 0.9f),
                                0.3f to scrimColor.copy(alpha = 0.6f),
                                0.6f to scrimColor.copy(alpha = 0.3f),
                                1f to Color.Transparent,
                            )
                        onDrawBehind { drawRect(brush = fade, alpha = scrollProgress().coerceIn(0f, 1f)) }
                    },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 14.dp, bottom = 16.dp, start = MochyHomeContentInset, end = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(Res.drawable.mochy_mark),
                    contentDescription = stringResource(Res.string.app_name),
                    tint = Color.White,
                    modifier =
                        Modifier
                            .mochyLaunchMarkTarget()
                            .size(MOCHY_MARK_WIDTH.dp, MOCHY_MARK_HEIGHT.dp)
                            .graphicsLayer {
                                alpha = 1f - topBarProgress
                                val scale = 1f - topBarProgress * 0.25f
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                renderEffect = if (blur) mochyBlur(topBarProgress * 15f) else null
                            },
                )
                Text(
                    text = stringResource(Res.string.home),
                    style =
                        TextStyle(
                            fontFamily = mochyInterFontFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = (-0.06).em,
                        ),
                    color = titleColor,
                    maxLines = 1,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = topBarProgress
                            renderEffect = if (blur) mochyBlur((1f - topBarProgress) * 15f) else null
                        },
                )
            }
            Box(
                modifier =
                    Modifier
                        .mochyLaunchRise(order = 0, distance = 16.dp)
                        .graphicsLayer {
                            val scale = 1f - topBarProgress * 0.1f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }.height(44.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailing()
            }
        }
    }
}

private const val MOCHY_MARK_WIDTH = 31
private const val MOCHY_MARK_HEIGHT = 34

private fun mochyBlur(radiusPx: Float): RenderEffect? = if (radiusPx < 0.5f) null else BlurEffect(radiusPx, radiusPx, TileMode.Decal)
