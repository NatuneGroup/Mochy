// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's own tab, its ProfileScreen: a dark header holding the profile ID card over a dot grid, a giant MOCHY
// column drifting up its right edge, then the listening time, the version, the people behind Mochy and the row
// into the settings. The header folds into a frosted bar with the greeting as the page scrolls.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.BuildConfig
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_card_local_member
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.generated.resources.mochy_profile_capsule_about
import com.metrolist.shared.generated.resources.mochy_profile_greeting_fallback
import com.metrolist.shared.generated.resources.mochy_profile_greeting_format
import com.metrolist.shared.generated.resources.mochy_profile_meet_dev
import com.metrolist.shared.generated.resources.mochy_profile_open_settings
import com.metrolist.shared.generated.resources.mochy_profile_settings_header
import com.metrolist.shared.generated.resources.mochy_profile_version_about_app
import com.metrolist.shared.generated.resources.mochy_set_settings
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.hypot

private val HeaderGrey = Color(0xFF1C1C1C)
private val HeaderText = Color(0xFF272727)
private val HeaderDots = Color(0xFF262626)
private val PageCard = Color(0xFF0F0F0F)

// the reference's layout: everything inset 36dp, the card 130dp under the status bar and 49dp of room under it
private val ContentInset = 36.dp
private val CardTop = 130.dp
private val CardRoomBelow = 49.dp
private val LogoTop = 32.dp
private val GreetingTop = 80.dp
private val HeaderCorner = 26.dp

// the giant MOCHY column: one size everywhere, its plane centred 86% across, drifting along its length
private val GiantFontSize = 178.dp
private const val GIANT_CENTER_X = 0.86f
private const val GIANT_DRIFT_MS = 20_000

private val PageTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = (-0.6).sp, color = Color.White)

@Composable
fun MochyProfileHub(
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCredits: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val settings = Settings.shared
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scroll = rememberScrollState()
    val greetingWord = remember { MochyGreetingWords.random() }
    val name = settings.mochyUsername.ifBlank { settings.accountName }.trim()
    val greeting =
        stringResource(
            Res.string.mochy_profile_greeting_format,
            stringResource(greetingWord),
            name.ifBlank { stringResource(Res.string.mochy_profile_greeting_fallback) },
        )
    val totalMs by produceState<Long?>(initialValue = null) { value = loadListeningTotalMs() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapse by remember { derivedStateOf { (scroll.value / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f) } }
    val giant = rememberGiantDrift()
    val measurer = rememberTextMeasurer()
    val font = mochyInterFontFamily()

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .clipBottomRounded(HeaderCorner)
                    .drawBehind { drawRect(HeaderGrey) }
                    .drawWithCache {
                        val fontPx = GiantFontSize.toPx()
                        val style =
                            TextStyle(
                                fontFamily = font,
                                fontWeight = FontWeight.Bold,
                                fontSize =
                                    (
                                        fontPx / density.density /
                                            density.fontScale
                                    ).sp,
                                letterSpacing = (-0.03).em,
                                color = HeaderText,
                            )
                        val layout = measurer.measure("MOCHY", style, softWrap = false, maxLines = 1, overflow = TextOverflow.Visible)
                        val tile = layout.size.width + fontPx * 0.22f
                        val span = hypot(size.width, size.height) * 1.1f
                        onDrawBehind {
                            translate(GIANT_CENTER_X * size.width, size.height / 2f) {
                                rotate(90f, pivot = Offset.Zero) {
                                    // the words slide along the column, tiled so it never runs out
                                    val base = -giant.value * tile
                                    val tiles = ceil((base + span / 2f) / tile)
                                    var x = base - tiles * tile
                                    val top = -layout.firstBaseline + 0.727f * fontPx / 2f
                                    while (x < span / 2f) {
                                        drawText(layout, topLeft = Offset(x, top))
                                        x += tile
                                    }
                                }
                            }
                        }
                    }.mochyDotGrid(HeaderDots),
            ) {
                val cardWidth = minOf(maxWidth - ContentInset * 2, 380.dp)
                val cardHeight = cardWidth / ID_CARD_ASPECT
                Box(Modifier.fillMaxWidth().height(statusBarTop + CardTop + cardHeight + CardRoomBelow)) {
                    Icon(
                        painter = painterResource(Res.drawable.mochy_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(start = ContentInset, top = statusBarTop + LogoTop).size(width = 31.dp, height = 34.dp),
                    )
                    Text(
                        text = greeting,
                        style = PageTitle.copy(fontFamily = font),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .padding(start = ContentInset, end = ContentInset, top = statusBarTop + GreetingTop)
                                .graphicsLayer { alpha = (1f - collapse * 1.6f).coerceIn(0f, 1f) },
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = statusBarTop + CardTop)
                            .widthIn(max = 380.dp)
                            .width(cardWidth)
                            .aspectRatio(ID_CARD_ASPECT),
                    ) {
                        val cardStats by produceState<MochyCardStats?>(initialValue = null) { value = loadMochyCardStats() }
                        InteractiveProfileIdCard(
                            username = truncateDisplayName(name),
                            photo = settings.mochyProfilePhotoUri.ifBlank { settings.accountThumbnail }.takeIf { it.isNotBlank() },
                            cardNumber = remember { ensureMochyCardNumber() },
                            joinedText = stringResource(Res.string.mochy_card_local_member),
                            silver = settings.mochyProfileCardSilver,
                            modifier = Modifier.fillMaxSize(),
                            backStats = cardStats,
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                MochyListeningTimeCard(totalMs ?: 0L, 1f, onViewStats = onOpenStats)
                Spacer(Modifier.height(16.dp))
                VersionCard(onClick = onOpenAbout)
                Spacer(Modifier.height(16.dp))
                CreditsCard(onClick = onOpenCredits)
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(Res.string.mochy_profile_settings_header),
                    style = PageTitle.copy(fontFamily = font, fontSize = 15.sp),
                    modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
                )
                OpenSettingsRow(onClick = onOpenSettings)
                Spacer(Modifier.height(180.dp + bottomPadding))
            }
        }

        // the frosted bar the header folds into, with the greeting beside the logo
        if (collapse > 0.005f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 84.dp)
                    .graphicsLayer { alpha = collapse }
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent))),
            )
            Icon(
                painter = painterResource(Res.drawable.mochy_mark),
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier
                        .padding(start = ContentInset, top = statusBarTop + LogoTop)
                        .size(width = 31.dp, height = 34.dp)
                        .graphicsLayer {
                            val k = 1f - collapse * 0.18f
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
            )
            Text(
                text = greeting,
                style = PageTitle.copy(fontFamily = font, fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(start = ContentInset + 40.dp, end = ContentInset, top = statusBarTop + 37.dp)
                        .graphicsLayer {
                            val p = ((collapse - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
                            alpha = eased
                            translationY = (1f - eased) * 12.dp.toPx()
                        },
            )
        }
    }
}

@Composable
private fun rememberGiantDrift() =
    rememberInfiniteTransition(label = "mochyGiant").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GIANT_DRIFT_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "mochyGiantDrift",
    )

/** The square dot pattern behind the header: 17dp apart, 1.3dp dots. */
private fun Modifier.mochyDotGrid(color: Color): Modifier =
    drawBehind {
        val step = 17.dp.toPx()
        val radius = 1.3.dp.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(color, radius, Offset(x, y))
                x += step
            }
            y += step
        }
    }

private fun Modifier.clipBottomRounded(radius: androidx.compose.ui.unit.Dp): Modifier =
    drawWithCache {
        val r = radius.toPx()
        val path =
            Path().apply {
                addRoundRect(
                    RoundRect(rect = Rect(0f, 0f, size.width, size.height), bottomLeft = CornerRadius(r), bottomRight = CornerRadius(r)),
                )
            }
        onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
    }

@Composable
private fun VersionCard(onClick: () -> Unit) {
    val font = mochyInterFontFamily()
    BoxWithConstraints {
        val k = (maxWidth.value / 320f).coerceIn(0.72f, 1.2f)
        val density = androidx.compose.ui.platform.LocalDensity.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(PageCard)
                    .clickable(onClick = onClick)
                    .padding(20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.mochy_profile_version_about_app),
                    style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF9A9A9A)),
                )
                Text(
                    BuildConfig.APP_VERSION.substringBefore(' ').substringBefore('+'),
                    style =
                        TextStyle(
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = with(density) { (44f * k).dp.toSp() },
                            letterSpacing = (-2).sp,
                            color = Color.White,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF4D4D4D))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(Res.string.mochy_profile_capsule_about),
                    style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// the people behind Mochy: its credit, kept on its own tab as the reference has it
@Composable
private fun CreditsCard(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(PageCard)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.mochy_mark), contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.mochy_profile_meet_dev),
            style =
                TextStyle(
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.6).sp,
                    color = Color(0xFF9A9A9A),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenSettingsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PageCard)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.mochy_set_settings),
                contentDescription = null,
                tint = Color(0xFF232323),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            stringResource(Res.string.mochy_profile_open_settings),
            style = PageTitle.copy(fontFamily = mochyInterFontFamily(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
