// Inspired by the bedside clock in Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album_art
import com.metrolist.shared.generated.resources.close
import com.metrolist.shared.generated.resources.mochy_bedside_face_analog
import com.metrolist.shared.generated.resources.mochy_bedside_face_big
import com.metrolist.shared.generated.resources.mochy_bedside_face_stacked
import com.metrolist.shared.generated.resources.mochy_bedside_night_tint
import com.metrolist.shared.generated.resources.mochy_bedside_now_playing
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

// Mochy's warm salmon-orange night tint for resting OLED screens.
private val BedsideOrange = Color(0xFFF4A68C)
private val CoverShape = RoundedCornerShape(22.dp)
private val DotSize = 6.dp
private val DotGap = 8.dp
private val DriftRange = 8.dp

private const val DOTS_LINGER_MS = 1_400L
private const val CHROME_LINGER_MS = 4_000L
private const val REST_AFTER_MS = 15_000L
private const val SHOW_MS = 260
private const val HIDE_MS = 700
private const val SLEEP_IN_MS = 2_400
private const val DRIFT_EVERY_MS = 60_000L
private const val DRIFT_MOVE_MS = 4_000
private const val INK_DIM = 0.5f
private const val SOFT_ALPHA = 0.55f

private val DigitSlide = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 260f)
private val HandSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 140f)

internal data class BedsideClockText(
    val time: String,
    val date: String,
    val is24Hour: Boolean = true,
    val period: String? = null,
)

@Composable
internal expect fun bedsideClockText(epochMillis: Long): BedsideClockText

internal enum class BedsideClockFace(
    val labelRes: StringResource,
) {
    BIG(Res.string.mochy_bedside_face_big),
    STACKED(Res.string.mochy_bedside_face_stacked),
    ANALOG(Res.string.mochy_bedside_face_analog),
    ;

    companion object {
        fun fromStored(value: String): BedsideClockFace = entries.firstOrNull { it.name == value } ?: BIG
    }
}

@Immutable
private data class BedsideTime(
    val hours: String,
    val minutes: String,
    val period: String?,
    val date: String,
    val hour24: Int,
    val minute: Int,
)

@Stable
private class BedsideRest {
    var fingerDown by mutableStateOf(false)
    var wakes by mutableIntStateOf(0)
        private set
    var chromeShown by mutableStateOf(true)
        private set
    var resting by mutableStateOf(false)
        private set
    private var dotsShown by mutableStateOf(false)

    val chrome = Animatable(1f)
    val dots = Animatable(0f)
    val sleep = Animatable(0f)
    val drift = Animatable(Offset.Zero, Offset.VectorConverter)

    fun wake() {
        wakes++
    }

    suspend fun settle() {
        chromeShown = true
        dotsShown = true
        resting = false
        if (fingerDown) return
        delay(DOTS_LINGER_MS)
        dotsShown = false
        delay(CHROME_LINGER_MS - DOTS_LINGER_MS)
        chromeShown = false
        delay(REST_AFTER_MS - CHROME_LINGER_MS)
        resting = true
    }

    suspend fun followDots() {
        snapshotFlow { dotsShown }.collectLatest { shown ->
            dots.animateTo(if (shown) 1f else 0f, tween(if (shown) SHOW_MS else HIDE_MS))
        }
    }
}

@Stable
private class BedsideInk(
    private val rest: BedsideRest,
    private val tintEnabled: State<Boolean>,
) {
    fun ink(): Color {
        val sleep = rest.sleep.value
        val base = if (tintEnabled.value) lerp(Color.White, BedsideOrange, sleep) else Color.White
        return base.copy(alpha = 1f - INK_DIM * sleep)
    }

    val main = ColorProducer { ink() }
    val soft = ColorProducer { ink().let { it.copy(alpha = it.alpha * SOFT_ALPHA) } }

    fun tinting(): Boolean = rest.sleep.value > 0f
}

@Composable
private fun rememberBedsideRest(): BedsideRest {
    val rest = remember { BedsideRest() }
    val driftPx = with(LocalDensity.current) { DriftRange.toPx() }
    LaunchedEffect(rest) {
        snapshotFlow { rest.fingerDown to rest.wakes }.collectLatest { rest.settle() }
    }
    LaunchedEffect(rest) {
        snapshotFlow { rest.chromeShown }.collectLatest { shown ->
            rest.chrome.animateTo(if (shown) 1f else 0f, tween(if (shown) SHOW_MS else HIDE_MS))
        }
    }
    LaunchedEffect(rest) { rest.followDots() }
    LaunchedEffect(rest) {
        snapshotFlow { rest.resting }.collectLatest { resting ->
            rest.sleep.animateTo(if (resting) 1f else 0f, tween(if (resting) SLEEP_IN_MS else SHOW_MS))
        }
    }
    LaunchedEffect(rest, driftPx) {
        snapshotFlow { rest.resting }.collectLatest { drifting ->
            if (!drifting) {
                rest.drift.animateTo(Offset.Zero, tween(SHOW_MS))
                return@collectLatest
            }
            while (true) {
                delay(DRIFT_EVERY_MS)
                val target =
                    Offset(
                        x = (Random.nextFloat() * 2f - 1f) * driftPx,
                        y = (Random.nextFloat() * 2f - 1f) * driftPx,
                    )
                rest.drift.animateTo(target, tween(DRIFT_MOVE_MS))
            }
        }
    }
    return rest
}

private fun Modifier.watchBedsideTouches(rest: BedsideRest): Modifier =
    pointerInput(rest) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.any { it.pressed }
                if (down != rest.fingerDown) {
                    rest.fingerDown = down
                    if (down) rest.wake()
                }
            }
        }
    }

private fun Modifier.bedsideChrome(rest: BedsideRest): Modifier =
    graphicsLayer {
        alpha = rest.chrome.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

private fun Modifier.nightTint(ink: BedsideInk): Modifier =
    graphicsLayer {
        compositingStrategy = if (ink.tinting()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (ink.tinting()) {
            drawRect(ink.ink(), blendMode = BlendMode.Modulate)
        }
    }

@Composable
fun BedsideClock(
    title: String?,
    artist: String?,
    isPlaying: Boolean,
    onTogglePlay: (() -> Unit)?,
    onDismiss: () -> Unit,
    artworkUrl: String? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = Clock.System.now().toEpochMilliseconds()
                delay(60_000L - now.mod(60_000L))
            }
        }
    }

    val localeClock = bedsideClockText(now)
    val localDateTime =
        remember(now) {
            Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
        }
    val bedsideTime =
        remember(localeClock, localDateTime) {
            val hour24 = localDateTime.hour
            val minute = localDateTime.minute
            val shownHour =
                if (localeClock.is24Hour) {
                    hour24
                } else {
                    (hour24 % 12).takeIf { it != 0 } ?: 12
                }
            BedsideTime(
                hours = if (localeClock.is24Hour) shownHour.toString().padStart(2, '0') else shownHour.toString(),
                minutes = minute.toString().padStart(2, '0'),
                period = localeClock.period,
                date = localeClock.date,
                hour24 = hour24,
                minute = minute,
            )
        }

    var nightTintEnabled by remember { mutableStateOf(Settings.shared.bedsideNightTint) }
    val nightTintState = rememberUpdatedState(nightTintEnabled)
    val rest = rememberBedsideRest()
    val ink = remember(rest) { BedsideInk(rest, nightTintState) }

    val faces = BedsideClockFace.entries
    val initialFace = remember { BedsideClockFace.fromStored(Settings.shared.bedsideClockFace) }
    val facePager = rememberPagerState(initialPage = initialFace.ordinal) { faces.size }
    LaunchedEffect(facePager) {
        snapshotFlow { facePager.settledPage }
            .drop(1)
            .collectLatest { page ->
                Settings.shared.bedsideClockFace = faces[page].name
            }
    }

    val hasTrack = !title.isNullOrBlank()
    val sectionCount = if (hasTrack) 2 else 1
    val sectionPager = rememberPagerState(initialPage = 0) { sectionCount }

    val playPauseDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.play)
    val nightTintDescription = stringResource(Res.string.mochy_bedside_night_tint)
    val nowPlayingDescription = stringResource(Res.string.mochy_bedside_now_playing)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .watchBedsideTouches(rest),
        ) {
            VerticalPager(
                state = sectionPager,
                beyondViewportPageCount = if (hasTrack) 1 else 0,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val drift = rest.drift.value
                            translationX = drift.x
                            translationY = drift.y
                        },
            ) { page ->
                if (page == 0) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            HorizontalPager(
                                state = facePager,
                                modifier = Modifier.fillMaxSize(),
                                key = { faces[it].name },
                            ) { faceIndex ->
                                when (faces[faceIndex]) {
                                    BedsideClockFace.BIG -> BigClockFace(bedsideTime, ink)
                                    BedsideClockFace.STACKED -> StackedClockFace(bedsideTime, ink)
                                    BedsideClockFace.ANALOG -> AnalogClockFace(bedsideTime, ink)
                                }
                            }
                            PageDots(
                                count = faces.size,
                                position = { facePager.currentPage + facePager.currentPageOffsetFraction },
                                vertical = false,
                                rest = rest,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                            )
                        }

                        if (hasTrack) {
                            QuietTrackBar(
                                title = title.orEmpty(),
                                artist = artist,
                                isPlaying = isPlaying,
                                ink = ink,
                                playPauseDescription = playPauseDescription,
                                onTogglePlay = onTogglePlay,
                                onOpenNowPlaying = {
                                    rest.wake()
                                    scope.launch { sectionPager.animateScrollToPage(1) }
                                },
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                } else {
                    BedsideNowPlayingPage(
                        time = bedsideTime,
                        title = title.orEmpty(),
                        artist = artist,
                        artworkUrl = artworkUrl,
                        isPlaying = isPlaying,
                        ink = ink,
                        rest = rest,
                        playPauseDescription = playPauseDescription,
                        onTogglePlay = onTogglePlay,
                        onPrevious = onPrevious,
                        onNext = onNext,
                    )
                }
            }

            if (hasTrack) {
                PageDots(
                    count = 2,
                    position = { sectionPager.currentPage + sectionPager.currentPageOffsetFraction },
                    vertical = true,
                    rest = rest,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                )
            }

            // Top chrome: clock face chips on the start, night-tint + now-playing + close on the end.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .bedsideChrome(rest),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sectionPager.currentPage == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        faces.forEachIndexed { index, face ->
                            val selected = facePager.currentPage == index
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (selected) {
                                                Color.White.copy(alpha = 0.18f)
                                            } else {
                                                Color.White.copy(alpha = 0.06f)
                                            },
                                        ).clickable {
                                            rest.wake()
                                            Settings.shared.bedsideClockFace = face.name
                                            scope.launch { facePager.animateScrollToPage(index) }
                                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = stringResource(face.labelRes),
                                    color = Color.White.copy(alpha = if (selected) 0.96f else 0.64f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasTrack) {
                        IconButton(
                            onClick = {
                                rest.wake()
                                val target = if (sectionPager.currentPage == 0) 1 else 0
                                scope.launch { sectionPager.animateScrollToPage(target) }
                            },
                        ) {
                            Icon(
                                imageVector = MetrolistIcons.Rounded.MusicNote,
                                contentDescription = nowPlayingDescription,
                                tint =
                                    if (sectionPager.currentPage == 1) {
                                        BedsideOrange
                                    } else {
                                        Color.White.copy(alpha = 0.78f)
                                    },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            rest.wake()
                            nightTintEnabled = !nightTintEnabled
                            Settings.shared.bedsideNightTint = nightTintEnabled
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (nightTintEnabled) {
                                    MetrolistIcons.Filled.Bedtime
                                } else {
                                    MetrolistIcons.Outlined.Bedtime
                                },
                            contentDescription = nightTintDescription,
                            tint = if (nightTintEnabled) BedsideOrange else Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MetrolistIcons.Default.Close,
                            contentDescription = stringResource(Res.string.close),
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigClockFace(
    time: BedsideTime,
    ink: BedsideInk,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val digits = minOf(maxHeight * 0.46f, maxWidth / 3.0f, 200.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(time.date, style = labelStyle(digits * 0.13f), color = ink.soft)
            Spacer(Modifier.height(digits * 0.04f))
            Row(verticalAlignment = Alignment.Top) {
                FlipDigits("${time.hours}:${time.minutes}", clockStyle(digits, FontWeight.SemiBold), ink.main)
                time.period?.let { period ->
                    Spacer(Modifier.width(digits * 0.04f))
                    BasicText(period, style = labelStyle(digits * 0.14f), color = ink.soft)
                }
            }
        }
    }
}

@Composable
private fun StackedClockFace(
    time: BedsideTime,
    ink: BedsideInk,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val line = if (landscape) minOf(maxHeight * 0.42f, 180.dp) else minOf(maxWidth * 0.48f, maxHeight * 0.28f, 170.dp)
        val style = clockStyle(line, FontWeight.Bold, lineHeight = 0.88f)
        val numbers: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipDigits(time.hours.padStart(2, '0'), style, ink.main)
                FlipDigits(time.minutes, style, ink.soft)
            }
        }
        val dateBlock: @Composable () -> Unit = {
            Column(horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally) {
                BasicText(time.date, style = labelStyle(line * 0.15f), color = ink.soft)
                time.period?.let { period ->
                    BasicText(period, style = labelStyle(line * 0.15f), color = ink.soft)
                }
            }
        }
        if (landscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(line * 0.28f),
            ) {
                numbers()
                dateBlock()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(line * 0.12f),
            ) {
                numbers()
                dateBlock()
            }
        }
    }
}

@Composable
private fun AnalogClockFace(
    time: BedsideTime,
    ink: BedsideInk,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val dial = if (landscape) minOf(maxHeight * 0.72f, 320.dp) else minOf(maxWidth * 0.72f, maxHeight * 0.46f, 300.dp)
        val date: @Composable () -> Unit = {
            BasicText(time.date, style = labelStyle(dial * 0.075f), color = ink.soft)
        }
        if (landscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dial * 0.16f),
            ) {
                AnalogDial(time, ink, Modifier.size(dial))
                date()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dial * 0.1f),
            ) {
                AnalogDial(time, ink, Modifier.size(dial))
                date()
            }
        }
    }
}

@Composable
private fun AnalogDial(
    time: BedsideTime,
    ink: BedsideInk,
    modifier: Modifier,
) {
    val minuteTurn = remember { Animatable(minuteAngle(time)) }
    val hourTurn = remember { Animatable(hourAngle(time)) }
    LaunchedEffect(time.hour24, time.minute) {
        launch {
            minuteTurn.animateTo(minuteTurn.value + turnBetween(minuteTurn.value, minuteAngle(time)), HandSpring)
        }
        launch {
            hourTurn.animateTo(hourTurn.value + turnBetween(hourTurn.value, hourAngle(time)), HandSpring)
        }
    }
    Spacer(
        modifier.drawWithCache {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val ticks = FloatArray(60 * 4)
            for (i in 0 until 60) {
                val angle = (i * 6.0 - 90.0) * PI / 180.0
                val inner = if (i % 5 == 0) radius * 0.84f else radius * 0.92f
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                ticks[i * 4] = center.x + cosA * inner
                ticks[i * 4 + 1] = center.y + sinA * inner
                ticks[i * 4 + 2] = center.x + cosA * radius * 0.98f
                ticks[i * 4 + 3] = center.y + sinA * radius * 0.98f
            }
            val majorStroke = radius * 0.022f
            val minorStroke = radius * 0.01f
            onDrawBehind {
                val colour = ink.ink()
                val faint = colour.copy(alpha = colour.alpha * 0.35f)
                for (i in 0 until 60) {
                    val bold = i % 5 == 0
                    drawLine(
                        color = if (bold) colour else faint,
                        start = Offset(ticks[i * 4], ticks[i * 4 + 1]),
                        end = Offset(ticks[i * 4 + 2], ticks[i * 4 + 3]),
                        strokeWidth = if (bold) majorStroke else minorStroke,
                        cap = StrokeCap.Round,
                    )
                }
                rotate(hourTurn.value, center) {
                    drawLine(
                        color = colour,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.48f),
                        strokeWidth = radius * 0.055f,
                        cap = StrokeCap.Round,
                    )
                }
                rotate(minuteTurn.value, center) {
                    drawLine(
                        color = colour,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.74f),
                        strokeWidth = radius * 0.032f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(colour, radius * 0.045f, center)
                drawCircle(Color.Black, radius * 0.018f, center)
            }
        },
    )
}

@Composable
private fun FlipDigits(
    text: String,
    style: TextStyle,
    color: ColorProducer,
) {
    Row {
        text.forEachIndexed { index, char ->
            // Count from the right so minutes stay stationary when the hour gains or drops a digit.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(DigitSlide) { it / 3 } + fadeIn(tween(420))) togetherWith
                            (slideOutVertically(DigitSlide) { -it / 3 } + fadeOut(tween(260))) using
                            SizeTransform(clip = false)
                    },
                    contentAlignment = Alignment.Center,
                    label = "bedsideDigit",
                ) { shown ->
                    BasicText(shown.toString(), style = style, color = color)
                }
            }
        }
    }
}

@Composable
private fun QuietTrackBar(
    title: String,
    artist: String?,
    isPlaying: Boolean,
    ink: BedsideInk,
    playPauseDescription: String,
    onTogglePlay: (() -> Unit)?,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenNowPlaying)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = ink.main,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist?.takeIf(String::isNotBlank)?.let { subtitle ->
                BasicText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onTogglePlay != null) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.semantics { contentDescription = playPauseDescription },
            ) {
                AnimatedPlayGlyph(
                    playing = isPlaying,
                    color = ink.ink(),
                    height = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun BedsideNowPlayingPage(
    time: BedsideTime,
    title: String,
    artist: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    ink: BedsideInk,
    rest: BedsideRest,
    playPauseDescription: String,
    onTogglePlay: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val landscape = maxWidth > maxHeight
        val coverSize =
            if (landscape) {
                minOf(maxHeight * 0.68f, maxWidth * 0.36f, 280.dp)
            } else {
                minOf(maxWidth * 0.72f, maxHeight * 0.4f, 280.dp)
            }
        val titleSize = if (landscape) minOf(maxHeight * 0.075f, 30.dp) else minOf(maxWidth * 0.068f, 28.dp)

        val artworkBlock: @Composable () -> Unit = {
            Box(
                modifier =
                    Modifier
                        .size(coverSize)
                        .clip(CoverShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .nightTint(ink),
                contentAlignment = Alignment.Center,
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        url = artworkUrl,
                        contentDescription = stringResource(Res.string.album_art),
                        contentScale = ContentScale.Crop,
                        saturation = 1f - rest.sleep.value,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = MetrolistIcons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(coverSize * 0.35f),
                    )
                }
            }
        }

        val detailsBlock: @Composable (Modifier) -> Unit = { detailsModifier ->
            Column(
                modifier = detailsModifier,
                horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = "${time.hours}:${time.minutes}",
                    style = labelStyle(titleSize * 0.64f, FontWeight.SemiBold),
                    color = ink.soft,
                )
                Spacer(Modifier.height(titleSize * 0.45f))
                BasicText(
                    text = title,
                    style = labelStyle(titleSize, FontWeight.Bold),
                    color = ink.main,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                artist?.takeIf(String::isNotBlank)?.let { subtitle ->
                    Spacer(Modifier.height(titleSize * 0.2f))
                    BasicText(
                        text = subtitle,
                        style = labelStyle(titleSize * 0.66f),
                        color = ink.soft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onTogglePlay != null || onPrevious != null || onNext != null) {
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.bedsideChrome(rest),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onPrevious != null) {
                            BedsideTransportCircle(onClick = onPrevious) {
                                Icon(
                                    imageVector = MetrolistIcons.Rounded.SkipPrevious,
                                    contentDescription = stringResource(Res.string.previous),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        if (onTogglePlay != null) {
                            BedsideTransportCircle(
                                onClick = onTogglePlay,
                                modifier = Modifier.semantics { contentDescription = playPauseDescription },
                            ) {
                                AnimatedPlayGlyph(
                                    playing = isPlaying,
                                    color = Color.White,
                                    height = 20.dp,
                                )
                            }
                        }
                        if (onNext != null) {
                            BedsideTransportCircle(onClick = onNext) {
                                Icon(
                                    imageVector = MetrolistIcons.Rounded.SkipNext,
                                    contentDescription = stringResource(Res.string.next),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            ) {
                artworkBlock()
                detailsBlock(Modifier.weight(1f, fill = false))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                artworkBlock()
                detailsBlock(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BedsideTransportCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PageDots(
    count: Int,
    position: () -> Float,
    vertical: Boolean,
    rest: BedsideRest,
    modifier: Modifier,
) {
    val length = DotSize * count + DotGap * (count - 1)
    Spacer(
        modifier
            .size(if (vertical) DpSize(DotSize, length) else DpSize(length, DotSize))
            .drawBehind {
                val shown = rest.dots.value
                if (shown <= 0.01f) return@drawBehind
                val radius = DotSize.toPx() / 2f
                val step = DotSize.toPx() + DotGap.toPx()
                val at = position()
                for (i in 0 until count) {
                    val near = (1f - abs(at - i)).coerceIn(0f, 1f)
                    val along = radius + i * step
                    drawCircle(
                        color = Color.White.copy(alpha = shown * (0.3f + 0.7f * near)),
                        radius = radius,
                        center = if (vertical) Offset(radius, along) else Offset(along, radius),
                    )
                }
            },
    )
}

private fun minuteAngle(time: BedsideTime): Float = time.minute * 6f

private fun hourAngle(time: BedsideTime): Float = (time.hour24 % 12) * 30f + time.minute * 0.5f

private fun turnBetween(
    from: Float,
    to: Float,
): Float = ((to - from) % 360f + 540f) % 360f - 180f

@Composable
private fun clockStyle(
    size: Dp,
    weight: FontWeight,
    lineHeight: Float = 1f,
): TextStyle {
    val sp = dpToSp(size)
    return TextStyle(
        fontFamily = flagshipFontFamily(),
        fontWeight = weight,
        fontSize = sp,
        lineHeight = sp * lineHeight,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = "tnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
}

@Composable
private fun labelStyle(
    size: Dp,
    weight: FontWeight = FontWeight.Medium,
): TextStyle =
    TextStyle(
        fontFamily = flagshipFontFamily(),
        fontWeight = weight,
        fontSize = dpToSp(size),
        letterSpacing = (-0.02).em,
    )

@Composable
private fun dpToSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }
