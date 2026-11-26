// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.audiolab.StageBox
import com.metrolist.shared.audiolab.StageListener
import com.metrolist.shared.audiolab.StagePresets
import com.metrolist.shared.audiolab.StageSettings
import com.metrolist.shared.audiolab.StageSource
import com.metrolist.shared.audiolab.stageSettings
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.audio_lab_room_size
import com.metrolist.shared.generated.resources.audio_lab_stage
import com.metrolist.shared.generated.resources.audio_lab_stage_android_only
import com.metrolist.shared.generated.resources.audio_lab_stage_description
import com.metrolist.shared.generated.resources.audio_lab_stage_echo
import com.metrolist.shared.generated.resources.audio_lab_stage_level
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_around
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_front_row
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_hall
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_living
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_on_stage
import com.metrolist.shared.generated.resources.audio_lab_stage_preset_studio
import com.metrolist.shared.generated.resources.audio_lab_stage_unavailable
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** the Stage card: a small room that sways, the switch, the room presets and the room's three sliders */
@Composable
internal fun StageCard(support: AudioLabStageSupport) {
    val stage = Settings.shared.stageSettings()
    val usable = support == AudioLabStageSupport.Available
    val active = usable && stage.enabled
    val unavailableText: String? = if (usable) {
        null
    } else {
        stringResource(
            if (support == AudioLabStageSupport.AndroidOnly) {
                Res.string.audio_lab_stage_android_only
            } else {
                Res.string.audio_lab_stage_unavailable
            },
        )
    }
    val footer: (@Composable ColumnScope.() -> Unit)? = if (unavailableText == null) null else {
        { StageNote(unavailableText) }
    }
    EffectCard(
        picture = { StagePicture(stage) },
        header = {
            LabToggleRow(
                title = stringResource(Res.string.audio_lab_stage),
                subtitle = stringResource(Res.string.audio_lab_stage_description),
                checked = stage.enabled,
                onCheckedChange = { on -> saveStage(stage.copy(enabled = on)) },
                enabled = usable,
            )
        },
        expanded = active,
        options = {
            LabChips(
                chips = StagePresets.All.map { LabChip(it.id, stringResource(stagePresetName(it.id))) },
                selectedKey = stage.presetId,
                onSelect = { id ->
                    StagePresets.All.firstOrNull { it.id == id }?.let { preset ->
                        saveStage(StagePresets.apply(stage, preset))
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            LabPanel {
                LabSlider(
                    label = stringResource(Res.string.audio_lab_room_size),
                    valueText = percent(stage.roomSize),
                    value = stage.roomSize,
                    valueRange = 0f..1f,
                    enabled = active,
                    onValueChange = { saveStage(stage.copy(roomSize = it)) },
                )
                LabSlider(
                    label = stringResource(Res.string.audio_lab_stage_echo),
                    valueText = percent(stage.roomAmount),
                    value = stage.roomAmount,
                    valueRange = 0f..1f,
                    enabled = active,
                    onValueChange = { saveStage(stage.copy(roomAmount = it)) },
                )
                LabSlider(
                    label = stringResource(Res.string.audio_lab_stage_level),
                    valueText = decibels(stage.levelDb),
                    value = stage.levelDb,
                    valueRange = -12f..6f,
                    enabled = active,
                    onValueChange = { saveStage(stage.copy(levelDb = it)) },
                )
            }
        },
        footer = footer,
    )
}

@Composable
private fun ColumnScope.StageNote(text: String) {
    Text(
        text = text,
        fontFamily = mochyInterFontFamily(),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = LabColors.Secondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StagePicture(settings: StageSettings) {
    val on by animateFloatAsState(if (settings.enabled) 1f else 0f, tween(400), label = "stageCardOn")
    val preset = remember(settings.presetId) { StagePresets.byId(settings.presetId) }
    PictureCanvas { t, _ ->
        // a slow sway rather than a spin, so it still reads as the same room
        val yaw = DefaultCameraYaw + sin(t * 0.35f) * 22f * (0.4f + 0.6f * on)
        val view = StageView(size, settings.box, yaw, DefaultCameraPitch + 6f)
        val box = settings.box
        val marks = preset.objects.map { o ->
            StageMark(
                key = o.id,
                source = o.source,
                x = o.x,
                y = o.y,
                z = o.z,
                // no music tap yet, so nothing lights up
                level = 0f,
                spread = if (o.source == StageSource.Ambience) o.width * min(3f, box.width * 0.35f) / (box.width / 2) else 0f,
            )
        }
        drawStage(view, marks, preset.listener, on)
    }
}

private fun stagePresetName(id: String): StringResource =
    when (id) {
        "studio" -> Res.string.audio_lab_stage_preset_studio
        "living" -> Res.string.audio_lab_stage_preset_living
        "front_row" -> Res.string.audio_lab_stage_preset_front_row
        "on_stage" -> Res.string.audio_lab_stage_preset_on_stage
        "around" -> Res.string.audio_lab_stage_preset_around
        "hall" -> Res.string.audio_lab_stage_preset_hall
        else -> error("Unknown Stage preset: $id")
    }

// ---- the 3D room: a wire box, every sound as a dot, and the listener's head -------------------

private const val DefaultCameraYaw = -28f
private const val DefaultCameraPitch = 24f
private const val CameraDistance = 3.4f
private const val HeadSize = 0.068f

// the presets don't store a head turn, so the listener faces straight ahead, down the room's z axis
private const val ListenerYaw = 0f

private fun sourceColor(source: StageSource): Color = when (source) {
    StageSource.Vocal -> Color(0xFFF2D46B)
    StageSource.Center -> Color(0xFFF2A46B)
    StageSource.Left -> Color(0xFF6BBAF2)
    StageSource.Right -> Color(0xFFF28BA8)
    StageSource.Ambience -> Color(0xFF6BD9C4)
    StageSource.Sub -> Color(0xFFB98CF2)
}

/** a point on screen with how far it is from the camera (bigger is further) and how much nearness scales it */
private class Projected(val point: Offset, val depth: Float, val scale: Float)

/** the room fitted to the canvas, seen from the camera's angle; positions go in as the settings keep them (x, z -1..1, y 0..1) */
private class StageView(size: Size, box: StageBox, yawDegrees: Float, pitchDegrees: Float) {
    // the room scaled so its longest side is 2 units, centred on the origin
    private val unit = 1f / max(box.width, max(box.depth, box.height)) * 2f
    private val halfX = box.width / 2 * unit
    private val halfY = box.height / 2 * unit
    private val halfZ = box.depth / 2 * unit

    private val yawCos = cos(degreesToRadians(yawDegrees.toDouble())).toFloat()
    private val yawSin = sin(degreesToRadians(yawDegrees.toDouble())).toFloat()
    private val pitchCos = cos(degreesToRadians(pitchDegrees.toDouble())).toFloat()
    private val pitchSin = sin(degreesToRadians(pitchDegrees.toDouble())).toFloat()
    private val center = Offset(size.width / 2, size.height / 2)

    // fitted to the box's bounding sphere, so it never grows or shrinks as the room turns
    private val focal: Float = run {
        val radius = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)
        min(size.width, size.height) / 2 * 1.08f * sqrt(CameraDistance * CameraDistance - radius * radius) / radius
    }

    /** how flat the floor looks from up here, 0 edge-on .. 1 straight down */
    val floorSquash = abs(pitchSin)

    /** a size in room units as pixels at a projected point */
    fun pixels(at: Projected, worldSize: Float) = focal * worldSize / at.depth

    fun worldX(x: Float) = x * halfX
    fun worldY(y: Float) = (y - 0.5f) * 2 * halfY
    fun worldZ(z: Float) = z * halfZ

    fun project(x: Float, y: Float, z: Float): Projected = projectWorld(worldX(x), worldY(y), worldZ(z))

    fun projectWorld(wx: Float, wy: Float, wz: Float): Projected {
        val x1 = wx * yawCos + wz * yawSin
        val z1 = -wx * yawSin + wz * yawCos
        val y2 = wy * pitchCos + z1 * pitchSin
        val z2 = -wy * pitchSin + z1 * pitchCos
        val depth = z2 + CameraDistance
        val k = focal / depth
        return Projected(Offset(center.x + x1 * k, center.y - y2 * k), depth, CameraDistance / depth)
    }
}

/** one dot in the room, already resolved from the preset for drawing */
private class StageMark(
    val key: Int,
    val source: StageSource,
    val x: Float,
    val y: Float,
    val z: Float,
    /** 0..1, how loud it is right now */
    val level: Float,
    /** the ambience pair's half spread in settings x, 0 for everything else */
    val spread: Float,
)

private val Edges = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 3), intArrayOf(3, 2), intArrayOf(2, 0),
    intArrayOf(4, 5), intArrayOf(5, 7), intArrayOf(7, 6), intArrayOf(6, 4),
    intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
)

private fun DrawScope.drawStage(view: StageView, marks: List<StageMark>, listener: StageListener, on: Float) {
    drawRoomBox(view, on)
    drawFacing(view, listener, on)

    // far things first, so nearer ones cover them as the room turns
    val headDepth = view.project(listener.x, listener.y, listener.z).depth
    val ordered = marks.sortedByDescending { view.project(it.x, it.y, it.z).depth }
    var headDrawn = false
    for (mark in ordered) {
        if (!headDrawn && view.project(mark.x, mark.y, mark.z).depth < headDepth) {
            drawHead(view, listener, on)
            headDrawn = true
        }
        drawMark(view, mark, on)
    }
    if (!headDrawn) drawHead(view, listener, on)
}

private fun DrawScope.drawRoomBox(view: StageView, on: Float) {
    val grid = Color.White.copy(alpha = 0.035f + 0.03f * on)
    for (i in 0..4) {
        val t = -1f + i * 0.5f
        drawLine(grid, view.project(t, 0f, -1f).point, view.project(t, 0f, 1f).point, 1.dp.toPx())
        drawLine(grid, view.project(-1f, 0f, t).point, view.project(1f, 0f, t).point, 1.dp.toPx())
    }
    val corners = Array(8) { i ->
        view.project(if (i and 1 == 0) -1f else 1f, if (i and 4 == 0) 0f else 1f, if (i and 2 == 0) -1f else 1f)
    }
    val farthest = corners.maxOf { it.depth }
    for ((a, b) in Edges) {
        // the edges round the back sit a little dimmer, which is most of what reads as depth in a wire box
        val back = corners[a].depth == farthest || corners[b].depth == farthest
        val alpha = (if (back) 0.18f else 0.34f) * (0.6f + 0.4f * on)
        drawLine(Color.White.copy(alpha = alpha), corners[a].point, corners[b].point, 1.dp.toPx(), cap = StrokeCap.Round)
    }
}

// a soft wedge on the floor the way the head is facing
private fun DrawScope.drawFacing(view: StageView, listener: StageListener, on: Float) {
    val base = degreesToRadians(ListenerYaw.toDouble())
    val reach = 0.55f
    val path = Path()
    val start = view.projectWorld(view.worldX(listener.x), view.worldY(0f), view.worldZ(listener.z)).point
    path.moveTo(start.x, start.y)
    for (step in -4..4) {
        val angle = base + degreesToRadians(step * 6.0)
        val wx = view.worldX(listener.x) + sin(angle).toFloat() * reach
        val wz = view.worldZ(listener.z) + cos(angle).toFloat() * reach
        val p = view.projectWorld(wx, view.worldY(0f), wz).point
        path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(
        path,
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.12f * (0.5f + 0.5f * on)), Color.Transparent),
            center = start,
            radius = view.pixels(view.project(listener.x, 0f, listener.z), reach) + 1f,
        ),
    )
}

private fun DrawScope.drawStem(view: StageView, x: Float, y: Float, z: Float, color: Color, alpha: Float) {
    val top = view.project(x, y, z)
    val floor = view.project(x, 0f, z)
    val rx = 8.dp.toPx() * floor.scale
    val ry = rx * (0.2f + 0.7f * view.floorSquash)
    drawOval(color.copy(alpha = 0.2f * alpha), Offset(floor.point.x - rx, floor.point.y - ry), Size(rx * 2, ry * 2))
    drawLine(
        color.copy(alpha = 0.32f * alpha),
        floor.point,
        top.point,
        1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

private fun DrawScope.drawMark(view: StageView, mark: StageMark, on: Float) {
    val color = sourceColor(mark.source)
    val alpha = 0.55f + 0.45f * on
    val p = view.project(mark.x, mark.y, mark.z)
    drawStem(view, mark.x, mark.y, mark.z, color, alpha)

    if (mark.spread > 0f) {
        // the ambience plays as a pair either side of its dot
        val left = view.project((mark.x - mark.spread).coerceIn(-1f, 1f), mark.y, mark.z).point
        val right = view.project((mark.x + mark.spread).coerceIn(-1f, 1f), mark.y, mark.z).point
        drawLine(color.copy(alpha = 0.3f * alpha), left, right, 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color.copy(alpha = 0.5f * alpha), 2.5.dp.toPx() * p.scale, left)
        drawCircle(color.copy(alpha = 0.5f * alpha), 2.5.dp.toPx() * p.scale, right)
    }

    val base = 3.5.dp.toPx() * p.scale
    val r = base * (1f + 0.6f * mark.level * on)
    val glow = r * (2.4f + 1.4f * mark.level * on)
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.42f * alpha), Color.Transparent), center = p.point, radius = glow),
        radius = glow,
        center = p.point,
    )
    if (mark.source == StageSource.Sub) {
        // the sub is a box, like the speaker it stands for
        drawRoundRect(color.copy(alpha = alpha), Offset(p.point.x - r, p.point.y - r), Size(r * 2, r * 2), CornerRadius(r * 0.4f))
    } else {
        drawCircle(color.copy(alpha = alpha), r, p.point)
    }
    drawCircle(Color.White.copy(alpha = 0.55f * alpha), r * 0.32f, p.point + Offset(-r * 0.3f, -r * 0.3f))
}

// a little head in headphones: ears for cups and a nose showing where it looks, turning with the room
private fun DrawScope.drawHead(view: StageView, listener: StageListener, on: Float) {
    val alpha = 0.7f + 0.3f * on
    drawStem(view, listener.x, listener.y, listener.z, Color.White, alpha)
    val cx = view.worldX(listener.x)
    val cy = view.worldY(listener.y)
    val cz = view.worldZ(listener.z)
    val center = view.projectWorld(cx, cy, cz)
    val headSize = HeadSize * 0.8f
    val radius = max(view.pixels(center, headSize), 5.dp.toPx())
    val yaw = degreesToRadians(ListenerYaw.toDouble())
    val fx = sin(yaw).toFloat()
    val fz = cos(yaw).toFloat()
    // the head's right hand side, a quarter turn from where it faces
    val rx = fz
    val rz = -fx

    val cup = Color(0xFFE5E5EA).copy(alpha = alpha)
    val cupLeft = view.projectWorld(cx + rx * -1.02f * headSize, cy, cz + rz * -1.02f * headSize)
    val cupRight = view.projectWorld(cx + rx * 1.02f * headSize, cy, cz + rz * 1.02f * headSize)
    val nose = view.projectWorld(cx + fx * headSize * 1.02f, cy - 0.05f * headSize, cz + fz * headSize * 1.02f)

    val parts = listOf(
        Triple(cupLeft.depth, cupLeft.point, radius * 0.36f to cup),
        Triple(cupRight.depth, cupRight.point, radius * 0.36f to cup),
        Triple(nose.depth, nose.point, radius * 0.2f to Color(0xFFF2C6B4).copy(alpha = alpha)),
    )
    val (behind, front) = parts.partition { it.first > center.depth }
    behind.forEach { drawCircle(it.third.second, it.third.first, it.second) }
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFF7F7F9).copy(alpha = alpha), Color(0xFF8E8E96).copy(alpha = alpha)),
            center = center.point + Offset(-radius * 0.35f, -radius * 0.4f),
            radius = radius * 1.7f,
        ),
        radius = radius,
        center = center.point,
    )
    front.forEach { drawCircle(it.third.second, it.third.first, it.second) }

    // the headphones' band, over the top from cup to cup
    val top = view.projectWorld(cx, cy + 1.25f * headSize, cz).point
    val control = top * 2f - (cupLeft.point + cupRight.point) / 2f
    drawPath(
        Path().apply {
            moveTo(cupLeft.point.x, cupLeft.point.y)
            quadraticTo(control.x, control.y, cupRight.point.x, cupRight.point.y)
        },
        Color.White.copy(alpha = 0.9f * alpha),
        style = Stroke(max(1.5.dp.toPx(), radius * 0.16f), cap = StrokeCap.Round),
    )
}
