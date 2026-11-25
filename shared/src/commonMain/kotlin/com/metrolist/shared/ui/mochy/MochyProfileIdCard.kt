// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_add_profile_picture
import com.metrolist.shared.generated.resources.mochy_card_back_minutes_label
import com.metrolist.shared.generated.resources.mochy_card_back_no_stats
import com.metrolist.shared.generated.resources.mochy_card_back_top_artist
import com.metrolist.shared.generated.resources.mochy_card_celebrate_action
import com.metrolist.shared.generated.resources.mochy_card_celebrate_close
import com.metrolist.shared.generated.resources.mochy_card_default_name
import com.metrolist.shared.generated.resources.mochy_card_drag_hint
import com.metrolist.shared.generated.resources.mochy_card_finish_dark
import com.metrolist.shared.generated.resources.mochy_card_finish_silver
import com.metrolist.shared.generated.resources.mochy_card_id_of
import com.metrolist.shared.generated.resources.mochy_card_local_member
import com.metrolist.shared.generated.resources.mochy_card_of_name
import com.metrolist.shared.generated.resources.mochy_card_profile_usr
import com.metrolist.shared.generated.resources.mochy_hello
import com.metrolist.shared.generated.resources.mochy_profile_change_photo
import com.metrolist.shared.generated.resources.mochy_profile_choose_photo
import com.metrolist.shared.generated.resources.mochy_profile_picture
import com.metrolist.shared.generated.resources.mochy_profile_remove_photo
import com.metrolist.shared.generated.resources.mochy_username_label
import com.metrolist.shared.generated.resources.mochy_username_placeholder
import com.metrolist.shared.generated.resources.mochy_welcome_to_mochy
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Width-to-height ratio of Mochy's profile ID card (332 x 176). */
const val ID_CARD_ASPECT = 1.89f

private const val MARQUEE_PERIOD_NANOS = 5_200_000_000f

/** Ensures a persistent 4-digit local member number exists in [Settings.shared]. */
fun ensureMochyCardNumber(): String {
    val existing = Settings.shared.mochyProfileCardNumber
    if (existing.length == 4 && existing.all { it.isDigit() }) return existing
    val generated = Random.nextInt(1, 10_000).toString().padStart(4, '0')
    Settings.shared.mochyProfileCardNumber = generated
    return generated
}

/** Tracks the card's photo, name, and number bounds for flying morph animations. */
class IdCardAnchors {
    var root: LayoutCoordinates? = null
    var photo: LayoutCoordinates? = null
    var name: LayoutCoordinates? = null
    var number: LayoutCoordinates? = null
    var nameLayout: TextLayoutResult? = null
    var numberLayout: TextLayoutResult? = null

    val isReady: Boolean
        get() = listOf(root, photo, name, number).all { it?.isAttached == true } && nameLayout != null && numberLayout != null
}

/**
 * Mochy's signature local profile ID card, rendered in obsidian dark or brushed silver with a
 * live diagonal sheen and vertical rotating side marquee.
 */
@Composable
fun ProfileIdCard(
    username: String,
    photo: Any?,
    cardNumber: String,
    joinedText: String,
    modifier: Modifier = Modifier,
    silver: Boolean = false,
    showIdCardOf: Boolean = silver,
    sheen: () -> Float = { 0.5f },
    onPhotoClick: (() -> Unit)? = null,
    anchors: IdCardAnchors? = null,
    partsAlpha: () -> Float = { 1f },
    marqueeRunning: () -> Boolean = { true },
    showEditHint: Boolean = false,
    pressed: () -> Boolean = { false },
) {
    val defaultName = stringResource(Res.string.mochy_card_default_name)
    val name = username.trim().ifEmpty { defaultName }
    val shape = RoundedCornerShape(22.dp)
    val bodyText = if (silver) Color(0xFF111114) else Color.White
    val mutedText = if (silver) Color(0xFF3A3A40) else Color(0xFFB5B5B5)

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ID_CARD_ASPECT)
                .then(if (anchors != null) Modifier.onPlaced { anchors.root = it } else Modifier)
                .clip(shape)
                .then(if (silver) Modifier.silverSurface(sheen) else Modifier.background(Color(0xFF2F2F2F)))
                .drawWithContent {
                    drawContent()
                    if (pressed()) drawRect(Color.White.copy(alpha = if (silver) 0.28f else 0.14f))
                },
    ) {
        val unit = maxWidth / 332f
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = unit * 22f, top = unit * 20f, end = unit * 12f, bottom = unit * 18f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.mochy_card_profile_usr, cardNumber),
                        color = mutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (unit.value * 11f).sp,
                        maxLines = 1,
                        onTextLayout = { anchors?.numberLayout = it },
                        modifier = Modifier.flyingPart(anchors, partsAlpha) { number = it },
                    )
                    if (showEditHint) EditHint(mutedText, unit * 10f, partsAlpha)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(unit * 96f)
                                .flyingPart(anchors, partsAlpha) { this.photo = it }
                                .clip(CircleShape)
                                .background(if (silver) Color(0xFF5A5A60) else Color(0xFF4F4F4F))
                                .then(if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        val photoStr = (photo as? String)?.takeIf { it.isNotBlank() }
                        if (photo != null && (photo !is String || photoStr != null)) {
                            AsyncImage(
                                model = photo,
                                contentDescription = stringResource(Res.string.mochy_profile_picture),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (onPhotoClick != null) {
                            Icon(
                                imageVector = MetrolistIcons.Filled.Add,
                                contentDescription = stringResource(Res.string.mochy_add_profile_picture),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(unit * 34f),
                            )
                        } else {
                            val initial = firstDisplayCluster(name)
                            Text(
                                text = initial,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = (unit.value * 36f).sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(unit * 18f))
                    Column(modifier = Modifier.weight(1f)) {
                        if (showIdCardOf) {
                            Text(
                                text = stringResource(Res.string.mochy_card_id_of),
                                color = mutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (unit.value * 10f).sp,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var nameCut by remember { mutableStateOf(false) }
                            Text(
                                text = name,
                                color = bodyText,
                                fontSize = (unit.value * 21f).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = {
                                    anchors?.nameLayout = it
                                    nameCut = it.hasVisualOverflow
                                },
                                modifier =
                                    Modifier
                                        .weight(1f, fill = false)
                                        .flyingPart(anchors, partsAlpha) { this.name = it },
                            )
                            if (showEditHint && !nameCut) EditHint(bodyText.copy(alpha = 0.7f), unit * 12f, partsAlpha)
                        }
                        Text(
                            text = joinedText,
                            color = bodyText.copy(alpha = 0.85f),
                            fontSize = (unit.value * 10.5f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            MarqueeStrip(
                text = stringResource(Res.string.mochy_card_of_name, name),
                color = if (silver) Color(0xFF2B2B30).copy(alpha = 0.75f) else Color(0xFF8A8A8A),
                background = if (silver) Color.Black.copy(alpha = 0.18f) else Color(0xFF1B1B1B),
                modifier =
                    Modifier
                        .width(unit * 40f)
                        .fillMaxHeight(),
                fontSizeSp = unit.value * 17f,
                running = marqueeRunning,
            )
        }
    }
}

/** Listening numbers printed on the back of the profile ID card. Null when there is no stats source. */
data class MochyCardStats(
    val minutesListened: Long,
    val topArtist: String?,
)

/** Alternating bar and gap widths for the card back's decorative barcode, stable for a given number. */
private fun barcodeRuns(cardNumber: String): List<Float> {
    val random = Random(cardNumber.hashCode())
    return List(44) { 1f + random.nextInt(3) }
}

/**
 * Back face of the Mochy profile ID card: a barcode drawn from the card number and, when [stats]
 * is available, the listening numbers. Same geometry and finish as [ProfileIdCard].
 */
@Composable
fun ProfileIdCardBack(
    username: String,
    cardNumber: String,
    stats: MochyCardStats?,
    modifier: Modifier = Modifier,
    silver: Boolean = false,
    sheen: () -> Float = { 0.5f },
) {
    val defaultName = stringResource(Res.string.mochy_card_default_name)
    val name = username.trim().ifEmpty { defaultName }
    val shape = RoundedCornerShape(22.dp)
    val bodyText = if (silver) Color(0xFF111114) else Color.White
    val mutedText = if (silver) Color(0xFF3A3A40) else Color(0xFFB5B5B5)
    val barColor = if (silver) Color(0xFF111114) else Color(0xFFD9D9D9)
    val runs = remember(cardNumber) { barcodeRuns(cardNumber) }
    val fontFamily = mochyInterFontFamily()

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ID_CARD_ASPECT)
                .clip(shape)
                .then(if (silver) Modifier.silverSurface(sheen) else Modifier.background(Color(0xFF2F2F2F))),
    ) {
        val unit = maxWidth / 332f
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = unit * 22f, top = unit * 20f, end = unit * 12f, bottom = unit * 18f),
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(unit * 60f)) {
                    val step = size.width / runs.sum()
                    var x = 0f
                    runs.forEachIndexed { index, run ->
                        if (index % 2 == 0) drawRect(barColor, Offset(x, 0f), Size(run * step, size.height))
                        x += run * step
                    }
                }
                Text(
                    text = stringResource(Res.string.mochy_card_profile_usr, cardNumber),
                    color = mutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (unit.value * 10f).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (stats == null) {
                    Text(
                        text = stringResource(Res.string.mochy_card_back_no_stats),
                        color = mutedText,
                        fontFamily = fontFamily,
                        fontSize = (unit.value * 11f).sp,
                    )
                } else {
                    val topArtist = stats.topArtist
                    Row(horizontalArrangement = Arrangement.spacedBy(unit * 18f)) {
                        Column {
                            Text(
                                text = stringResource(Res.string.mochy_card_back_minutes_label),
                                color = mutedText,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (unit.value * 9f).sp,
                                maxLines = 1,
                            )
                            Text(
                                text = stats.minutesListened.toString(),
                                color = bodyText,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = (unit.value * 21f).sp,
                                maxLines = 1,
                            )
                        }
                        if (topArtist != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.mochy_card_back_top_artist),
                                    color = mutedText,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = (unit.value * 9f).sp,
                                    maxLines = 1,
                                )
                                Text(
                                    text = topArtist,
                                    color = bodyText,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (unit.value * 15f).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            MarqueeStrip(
                text = stringResource(Res.string.mochy_card_of_name, name),
                color = if (silver) Color(0xFF2B2B30).copy(alpha = 0.75f) else Color(0xFF8A8A8A),
                background = if (silver) Color.Black.copy(alpha = 0.18f) else Color(0xFF1B1B1B),
                modifier =
                    Modifier
                        .width(unit * 40f)
                        .fillMaxHeight(),
                fontSizeSp = unit.value * 17f,
            )
        }
    }
}

/**
 * Interactive 3D-tiltable wrapper around [ProfileIdCard]: dragging tilts the card in 3D space,
 * sweeps the brushed silver sheen across the surface, and springs smoothly back on release.
 * A tap turns the card over to [ProfileIdCardBack] and back again.
 */
@Composable
fun InteractiveProfileIdCard(
    username: String,
    photo: Any?,
    cardNumber: String,
    joinedText: String,
    silver: Boolean,
    modifier: Modifier = Modifier,
    onPhotoClick: (() -> Unit)? = null,
    backStats: MochyCardStats? = null,
) {
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    val showBack by remember { derivedStateOf { flip.value >= 90f } }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                            scope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                        },
                        onDragCancel = {
                            scope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                            scope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.12f).coerceIn(-28f, 28f)) }
                        scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.12f).coerceIn(-22f, 22f)) }
                    }
                }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val target = if (showBack) 0f else 180f
                    scope.launch { flip.animateTo(target, tween(durationMillis = 600, easing = FastOutSlowInEasing)) }
                }.graphicsLayer {
                    rotationX = tiltX.value
                    rotationY = tiltY.value + flip.value
                    cameraDistance = 14f * density
                },
    ) {
        if (!showBack) {
            ProfileIdCard(
                username = username,
                photo = photo,
                cardNumber = cardNumber,
                joinedText = joinedText,
                silver = silver,
                onPhotoClick = onPhotoClick,
                sheen = { 0.5f + tiltY.value / 42f },
            )
        } else {
            // the back is mirrored in its own layer; the flip above turns it back the right way round
            ProfileIdCardBack(
                username = username,
                cardNumber = cardNumber,
                stats = backStats,
                silver = silver,
                sheen = { 0.5f + tiltY.value / 42f },
                modifier =
                    Modifier.graphicsLayer {
                        rotationY = 180f
                        cameraDistance = 14f * density
                    },
            )
        }
    }
}

/**
 * Shared local Mochy profile editor used in both onboarding and Settings so there is a single
 * source of truth across Android, Desktop, and iOS.
 */
@Composable
fun MochyProfileEditorContent(
    modifier: Modifier = Modifier,
    showCelebrateButton: Boolean = true,
    rememberPhotoPicker: (@Composable (onPicked: (uri: String) -> Unit) -> () -> Unit)? = null,
) {
    val settings = Settings.shared
    val cardNumber = remember { ensureMochyCardNumber() }
    var username by remember { mutableStateOf(settings.mochyUsername.ifBlank { settings.accountName }) }
    var silver by remember { mutableStateOf(settings.mochyProfileCardSilver) }
    var localPhotoUri by remember { mutableStateOf(settings.mochyProfilePhotoUri) }
    var showCelebration by remember { mutableStateOf(false) }

    val photoUrl = localPhotoUri.ifBlank { settings.accountThumbnail }.takeIf { it.isNotBlank() }
    val joinedText = stringResource(Res.string.mochy_card_local_member)
    val launchPhotoPicker =
        rememberPhotoPicker?.invoke { pickedUri ->
            localPhotoUri = pickedUri
            settings.mochyProfilePhotoUri = pickedUri
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InteractiveProfileIdCard(
            username = username,
            photo = photoUrl,
            cardNumber = cardNumber,
            joinedText = joinedText,
            silver = silver,
            onPhotoClick = launchPhotoPicker,
            modifier = Modifier.widthIn(max = 420.dp),
        )

        Text(
            text = stringResource(Res.string.mochy_card_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (launchPhotoPicker != null || localPhotoUri.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (launchPhotoPicker != null) {
                    OutlinedButton(onClick = launchPhotoPicker) {
                        Text(
                            stringResource(
                                if (localPhotoUri.isNotBlank()) {
                                    Res.string.mochy_profile_change_photo
                                } else {
                                    Res.string.mochy_profile_choose_photo
                                },
                            ),
                        )
                    }
                }
                if (localPhotoUri.isNotBlank()) {
                    TextButton(
                        onClick = {
                            localPhotoUri = ""
                            settings.mochyProfilePhotoUri = ""
                        },
                    ) {
                        Text(stringResource(Res.string.mochy_profile_remove_photo))
                    }
                }
            }
        }

        OutlinedTextField(
            value = username,
            onValueChange = { updated ->
                val clipped = truncateDisplayName(updated)
                username = clipped
                settings.mochyUsername = clipped
            },
            label = { Text(stringResource(Res.string.mochy_username_label)) },
            placeholder = { Text(stringResource(Res.string.mochy_username_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !silver,
                onClick = {
                    silver = false
                    settings.mochyProfileCardSilver = false
                },
                label = { Text(stringResource(Res.string.mochy_card_finish_dark)) },
            )
            FilterChip(
                selected = silver,
                onClick = {
                    silver = true
                    settings.mochyProfileCardSilver = true
                },
                label = { Text(stringResource(Res.string.mochy_card_finish_silver)) },
            )
        }

        if (showCelebrateButton) {
            FilledTonalButton(onClick = { showCelebration = true }) {
                Text(stringResource(Res.string.mochy_card_celebrate_action))
            }
        }
    }

    if (showCelebration) {
        Dialog(
            onDismissRequest = { showCelebration = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MochyHelloCardCelebration(
                username = username,
                photo = photoUrl,
                cardNumber = cardNumber,
                joinedText = joinedText,
                onDismiss = { showCelebration = false },
            )
        }
    }
}

/** Settings page wrapper around [MochyProfileEditorContent]. */
@Composable
fun MochyProfileSettingsScreen(
    modifier: Modifier = Modifier,
    rememberPhotoPicker: (@Composable (onPicked: (uri: String) -> Unit) -> () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MochyProfileEditorContent(
            showCelebrateButton = true,
            rememberPhotoPicker = rememberPhotoPicker,
        )
    }
}

private const val MAX_MOCHY_USERNAME_CLUSTERS = 32

internal fun truncateDisplayName(
    input: String,
    maxClusters: Int = MAX_MOCHY_USERNAME_CLUSTERS,
): String {
    if (input.isEmpty() || maxClusters <= 0) return ""
    var count = 0
    var index = 0
    while (index < input.length && count < maxClusters) {
        index = nextDisplayClusterEnd(input, index)
        count++
    }
    return input.substring(0, index)
}

internal fun firstDisplayCluster(input: String): String {
    if (input.isEmpty()) return ""
    val end = nextDisplayClusterEnd(input, 0)
    val cluster = input.substring(0, end)
    return if (cluster.length == 1) cluster[0].uppercaseChar().toString() else cluster
}

private fun nextDisplayClusterEnd(
    text: String,
    start: Int,
): Int {
    if (start >= text.length) return start
    var index = advanceCodePoint(text, start)
    val firstCodePoint = codePointAt(text, start)
    if (isRegionalIndicator(firstCodePoint) && index < text.length && isRegionalIndicator(codePointAt(text, index))) {
        index = advanceCodePoint(text, index)
    }
    while (index < text.length) {
        val cp = codePointAt(text, index)
        if (cp == 0x200D) {
            val afterZwj = advanceCodePoint(text, index)
            if (afterZwj < text.length) {
                index = advanceCodePoint(text, afterZwj)
                continue
            }
            index = afterZwj
            break
        }
        if (isClusterContinuation(cp)) {
            index = advanceCodePoint(text, index)
        } else {
            break
        }
    }
    return index
}

private fun advanceCodePoint(
    text: String,
    index: Int,
): Int {
    val ch = text[index]
    return if (ch.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
        index + 2
    } else {
        index + 1
    }
}

private fun codePointAt(
    text: String,
    index: Int,
): Int {
    val ch = text[index]
    if (ch.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
        val high = ch.code - 0xD800
        val low = text[index + 1].code - 0xDC00
        return 0x10000 + (high shl 10) + low
    }
    return ch.code
}

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

private fun isClusterContinuation(cp: Int): Boolean =
    cp == 0xFE0E ||
        cp == 0xFE0F ||
        cp == 0x20E3 ||
        cp in 0x1F3FB..0x1F3FF ||
        cp in 0x0300..0x036F ||
        cp in 0x1AB0..0x1AFF ||
        cp in 0x1DC0..0x1DFF ||
        cp in 0x20D0..0x20FF ||
        cp in 0xFE20..0xFE2F ||
        cp in 0xE0020..0xE007F

/**
 * Full-screen animated silver ID card celebration ported from Mochy's `HelloCardStep`:
 * orbiting silver ambient glow, expanding ripple ring, 90-piece silver metallic confetti burst,
 * 3D-draggable silver ID card, and animated "Hello. / Welcome to Mochy" footer.
 */
@Composable
fun MochyHelloCardCelebration(
    username: String,
    photo: Any?,
    cardNumber: String,
    joinedText: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    var time by remember { mutableFloatStateOf(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val confetti = remember(cardNumber) { SilverConfetti.burst(count = 90, seed = cardNumber.hashCode()) }

    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                time = (now - start) / 1_000_000_000f
            }
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E)), radius = 1400f))
                .clickable { currentOnDismiss() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                            scope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                        },
                        onDragCancel = {
                            scope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                            scope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.12f).coerceIn(-35f, 35f)) }
                        scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.12f).coerceIn(-35f, 35f)) }
                    }
                },
    ) {
        val cardWidth = min(maxWidth.value * 0.86f, 420f).dp
        val cardCenterFromTop = 0.42f

        // Soft orbiting silver ambient glow using pure radial gradients (uniform across all platforms).
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (time / 0.5f).coerceIn(0f, 1f) },
        ) {
            val c = Offset(size.width / 2f, size.height * cardCenterFromTop)
            for (i in 0 until 4) {
                val a = time * (0.6f + i * 0.17f) + i * 1.7f
                val orbCenter = c + Offset(cos(a) * size.width * 0.12f, sin(a * 1.3f) * size.width * 0.1f)
                val radius = size.width * (0.36f + 0.06f * i)
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFD7D9DE).copy(alpha = 0.18f - i * 0.03f), Color.Transparent),
                            center = orbCenter,
                            radius = radius,
                        ),
                    radius = radius,
                    center = orbCenter,
                )
            }
        }

        // Expanding reveal wave and ring behind the card.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * cardCenterFromTop)
            val far = hypot(size.width, size.height)
            val p = easeOutCubic((time / 0.9f).coerceIn(0f, 1f))
            if (p < 1f) drawCircle(Color.White.copy(alpha = 0.10f * (1f - p)), radius = far * p, center = c)
            val ring = easeOutCubic(((time - 0.12f) / 1.1f).coerceIn(0f, 1f))
            if (ring in 0.001f..0.999f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f * (1f - ring)),
                    radius = far * 0.75f * ring,
                    center = c,
                    style = Stroke(width = 3.dp.toPx() * (1f - ring) + 1f),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            confetti.draw(this, origin = Offset(size.width / 2f, size.height * cardCenterFromTop), time = time - 0.15f)
        }

        ProfileIdCard(
            username = username,
            photo = photo,
            cardNumber = cardNumber,
            joinedText = joinedText,
            silver = true,
            sheen = { 0.5f + (tiltY.value + cos(time * 1.4f) * 7f) / 45f },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (maxHeight * cardCenterFromTop - (cardWidth / ID_CARD_ASPECT) / 2).coerceAtLeast(0.dp))
                    .width(cardWidth)
                    .graphicsLayer {
                        val enter = easeOutBack((time / 0.75f).coerceIn(0f, 1f))
                        val s = 0.6f + 0.4f * enter
                        scaleX = s
                        scaleY = s
                        alpha = (time / 0.25f).coerceIn(0f, 1f)
                        rotationZ = -8f * enter
                        rotationY = (1f - enter) * -55f + tiltY.value + cos(time * 1.4f) * 7f
                        rotationX = tiltX.value + sin(time * 1.1f) * 4f
                        cameraDistance = 14f * density
                    },
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 28.dp, end = 28.dp, bottom = 40.dp)
                    .graphicsLayer {
                        val p = easeOutCubic(((time - 0.35f) / 0.6f).coerceIn(0f, 1f))
                        alpha = p
                        translationY = (1f - p) * 40.dp.toPx()
                    },
        ) {
            Text(
                text = stringResource(Res.string.mochy_hello),
                color = Color.White,
                fontSize = 64.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
            )
            Text(
                text = stringResource(Res.string.mochy_welcome_to_mochy),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.mochy_card_celebrate_close),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EditHint(
    tint: Color,
    size: Dp,
    alpha: () -> Float,
) {
    Icon(
        imageVector = MetrolistIcons.Outlined.Edit,
        contentDescription = null,
        tint = tint,
        modifier =
            Modifier
                .padding(start = size / 3f)
                .size(size)
                .graphicsLayer { this.alpha = alpha() },
    )
}

private fun Modifier.flyingPart(
    anchors: IdCardAnchors?,
    alpha: () -> Float,
    track: IdCardAnchors.(LayoutCoordinates) -> Unit,
): Modifier {
    if (anchors == null) return this
    return onPlaced { anchors.track(it) }.graphicsLayer { this.alpha = alpha() }
}

@Composable
internal fun MarqueeStrip(
    text: String,
    color: Color,
    background: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    running: () -> Boolean = { true },
) {
    val measurer = rememberTextMeasurer()
    val fontFamily = mochyInterFontFamily()
    val style =
        remember(color, fontSizeSp, fontFamily) {
            TextStyle(
                color = color,
                fontFamily = fontFamily,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    val layout = remember(text, style) { measurer.measure("$text   ·   ", style) }
    var progress by remember { mutableFloatStateOf(0f) }
    val isRunning by rememberUpdatedState(running)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            if (!isRunning()) {
                snapshotFlow { isRunning() }.first { it }
                last = 0L
            }
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) progress = (progress + (now - last) / MARQUEE_PERIOD_NANOS) % 1f
                last = now
            }
        }
    }
    Canvas(modifier = modifier.background(background)) {
        val rawWidth = layout.size.width.toFloat()
        val step = rawWidth.coerceAtLeast(1f)
        rotate(90f, pivot = Offset.Zero) {
            translate(left = 0f, top = -size.width / 2f - layout.size.height / 2f) {
                var x = -step + progress * step
                while (x < size.height) {
                    drawText(layout, topLeft = Offset(x, 0f))
                    x += step
                }
            }
        }
    }
}

private fun Modifier.silverSurface(sheen: () -> Float): Modifier =
    drawWithCache {
        val base =
            Brush.linearGradient(
                0f to Color(0xFFBFC1C6),
                0.35f to Color(0xFFE9EAEE),
                0.55f to Color(0xFFA9ABB2),
                0.8f to Color(0xFFD7D9DE),
                1f to Color(0xFF9C9EA5),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        val lines = (size.height / 3f).toInt()
        onDrawBehind {
            drawRect(base)
            for (i in 0 until lines) {
                val y = i * 3f
                drawLine(
                    color = Color.White.copy(alpha = if (i % 2 == 0) 0.05f else 0.025f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            val center = sheen().coerceIn(-0.2f, 1.2f) * size.width
            drawRect(
                brush =
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        start = Offset(center - size.width * 0.35f, 0f),
                        end = Offset(center + size.width * 0.15f, size.height),
                    ),
                blendMode = BlendMode.Screen,
            )
        }
    }

private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun easeOutBack(t: Float): Float {
    val c1 = 1.4f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private class SilverConfetti private constructor(
    private val pieces: List<Piece>,
) {
    private class Piece(
        val vx: Float,
        val vy: Float,
        val spin: Float,
        val flutter: Float,
        val w: Float,
        val h: Float,
        val round: Boolean,
        val color: Color,
    )

    fun draw(
        scope: DrawScope,
        origin: Offset,
        time: Float,
    ) = with(scope) {
        if (time <= 0f) return@with
        val unit = size.minDimension / 400f
        val gravity = 900f * unit
        pieces.forEach { p ->
            val x = origin.x + p.vx * unit * time + sin(time * p.flutter) * 10f * unit
            val y = origin.y + p.vy * unit * time + 0.5f * gravity * time * time
            val fade = (1f - ((time - 1.9f) / 0.9f)).coerceIn(0f, 1f)
            if (fade <= 0f || y > size.height + 20f) return@forEach
            val w = p.w * unit
            val h = p.h * unit * (0.35f + 0.65f * abs(cos(time * p.flutter)))
            rotate(degrees = p.spin * time, pivot = Offset(x, y)) {
                if (p.round) {
                    drawCircle(p.color.copy(alpha = p.color.alpha * fade), radius = w / 2f, center = Offset(x, y))
                } else {
                    drawRect(
                        color = p.color.copy(alpha = p.color.alpha * fade),
                        topLeft = Offset(x - w / 2f, y - h / 2f),
                        size = Size(w, h),
                    )
                }
            }
        }
    }

    companion object {
        private val palette =
            listOf(
                Color.White,
                Color(0xFFE9EAEE),
                Color(0xFFC4C6CC),
                Color(0xFF9EA1A8),
                Color(0xFFF5F5F7),
                Color(0xFF7D8088),
            )

        fun burst(
            count: Int,
            seed: Int,
        ): SilverConfetti {
            val random = Random(seed)
            val pieces =
                List(count) {
                    val angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 1.25f).toFloat()
                    val speed = 260f + random.nextFloat() * 420f
                    Piece(
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        spin = (random.nextFloat() - 0.5f) * 720f,
                        flutter = 4f + random.nextFloat() * 8f,
                        w = 5f + random.nextFloat() * 6f,
                        h = 3f + random.nextFloat() * 4f,
                        round = random.nextFloat() < 0.25f,
                        color = palette[random.nextInt(palette.size)],
                    )
                }
            return SilverConfetti(pieces)
        }
    }
}
