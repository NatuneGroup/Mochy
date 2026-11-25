// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// the welcome girl on screen: her layers drawn from the idle loop each frame, the phone between her and her fingers

package com.metrolist.shared.ui.onboarding.mochy.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_mark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ceil
import kotlin.math.roundToInt

// the layers decoded at the size she's drawn where the platform can, so the gpu never shrinks a big picture every frame
internal class GirlBitmaps(
    val base: ImageBitmap,
    val hair: ImageBitmap,
    val eyeLeft: ImageBitmap,
    val eyeRight: ImageBitmap,
    val eyeLeftShut: ImageBitmap,
    val eyeRightShut: ImageBitmap,
    val mouth: ImageBitmap,
    val hand: ImageBitmap,
    val motionLines: ImageBitmap,
)

/** a layer's encoded art at about [width] x [height] pixels, ready to draw */
internal expect fun sizedGirlImage(
    bytes: ByteArray,
    width: Int,
    height: Int,
): ImageBitmap

/** a picture bent over a grid of points, like Android's drawBitmapMesh; points are row by row in canvas units */
internal expect class GirlMesh(
    cols: Int,
    rows: Int,
) {
    fun draw(
        canvas: Canvas,
        image: ImageBitmap,
        verts: FloatArray,
    )
}

private suspend fun loadGirl(scale: Float): GirlBitmaps =
    withContext(Dispatchers.Default) {
        suspend fun load(layer: GirlLayer): ImageBitmap {
            val bytes = Res.readBytes("files/onboarding/${layer.file}.webp")
            val width = (layer.w * scale).roundToInt().coerceAtLeast(1)
            val height = (layer.h * scale).roundToInt().coerceAtLeast(1)
            return sizedGirlImage(bytes, width, height)
        }
        GirlBitmaps(
            base = load(GirlArt.Base),
            hair = load(GirlArt.Hair),
            eyeLeft = load(GirlArt.EyeLeft),
            eyeRight = load(GirlArt.EyeRight),
            eyeLeftShut = load(GirlArt.EyeLeftShut),
            eyeRightShut = load(GirlArt.EyeRightShut),
            mouth = load(GirlArt.Mouth),
            hand = load(GirlArt.Hand),
            motionLines = load(GirlArt.MotionLines),
        )
    }

// the girl, sized by the caller to the art frame's shape; screen fills her phone and can be a video later
@Composable
internal fun WelcomeGirl(
    motion: GirlMotion,
    seconds: FloatState,
    modifier: Modifier = Modifier,
    screen: @Composable BoxScope.() -> Unit = { MochyPhoneScreen() },
) {
    BoxWithConstraints(modifier) {
        val rawScale = if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
            constraints.maxWidth / GirlArt.FRAME_WIDTH
        } else {
            0.5f
        }
        val scale = rawScale.coerceAtLeast(0.05f)
        // decoded in 5% steps and never above the art's own size, so a tiny resize doesn't decode again
        val bitmapScale = (ceil(scale * 20f) / 20f).coerceIn(0.05f, 1f)
        val bits by produceState<GirlBitmaps?>(null, bitmapScale) { value = loadGirl(bitmapScale) }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(bits != null) {
            if (bits != null) appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 90f))
        }
        val painter = remember { GirlPainter() }
        val lift = with(LocalDensity.current) { 40.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    seconds.floatValue
                    val shown = appear.value
                    alpha = shown.coerceIn(0f, 1f)
                    translationY = (1f - shown) * lift
                    // breathing lifts her shoulders a touch from the bottom of the frame
                    val breath = motion.pose.breath
                    scaleX = 1f + 0.0012f * breath
                    scaleY = 1f + 0.0045f * breath
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawBody(it, scale, motion.pose, loaded) }
                    },
            )
            PhoneBody(motion, seconds, scale, screen)
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawHand(it, scale, motion.pose, loaded) }
                    },
            )
        }
    }
}

// what her phone shows for now: plain gray with the mochy mark
@Composable
internal fun BoxScope.MochyPhoneScreen() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF5C5C5C), Color(0xFF3B3B3B)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.mochy_mark),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(128f / 139f),
        )
    }
}

// the phone's white edge, black bezel and screen, drifting with her hand by layer props only
@Composable
private fun PhoneBody(motion: GirlMotion, seconds: FloatState, scale: Float, screen: @Composable BoxScope.() -> Unit) {
    val width = GirlArt.PHONE_WIDTH * scale
    val height = GirlArt.PHONE_HEIGHT * scale
    val left = (GirlArt.PHONE_CENTER_X - GirlArt.FRAME_LEFT) * scale - width / 2f
    val top = (GirlArt.PHONE_CENTER_Y - GirlArt.FRAME_TOP) * scale - height / 2f
    val placed = IntOffset(left.roundToInt(), top.roundToInt())
    val outer = GirlArt.PHONE_RADIUS * scale
    val border = GirlArt.PHONE_BORDER * scale
    val bezel = GirlArt.PHONE_BEZEL * scale
    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .offset { placed }
                .size(width.toDp(), height.toDp())
                .graphicsLayer {
                    seconds.floatValue
                    val pose = motion.pose
                    // where the phone's centre lands after her hand's drift, plus the pixel fraction the offset dropped
                    val movedX = pose.phoneMoveX(GirlArt.PHONE_CENTER_X, GirlArt.PHONE_CENTER_Y)
                    val movedY = pose.phoneMoveY(GirlArt.PHONE_CENTER_X, GirlArt.PHONE_CENTER_Y)
                    translationX = (movedX - GirlArt.PHONE_CENTER_X) * scale + (left - placed.x)
                    translationY = (movedY - GirlArt.PHONE_CENTER_Y) * scale + (top - placed.y)
                    rotationZ = GirlArt.PHONE_TILT + pose.phoneDeg
                }
                .background(Color.White, RoundedCornerShape(outer.toDp()))
                .padding(border.toDp())
                .background(Color.Black, RoundedCornerShape((outer - border).toDp()))
                .padding(bezel.toDp())
                .clip(RoundedCornerShape((outer - border - bezel).coerceAtLeast(0f).toDp())),
            content = screen,
        )
    }
}


// draws her straight onto the canvas: two bent meshes, small patches for the eyes and mouth
private class GirlPainter {
    private val paint = Paint().apply { filterQuality = FilterQuality.Low }
    private val baseMesh = GirlMesh(GirlPose.BASE_COLS, GirlPose.BASE_ROWS)
    private val hairMesh = GirlMesh(GirlPose.HAIR_COLS, GirlPose.HAIR_ROWS)

    fun drawBody(
        canvas: Canvas,
        scale: Float,
        pose: GirlPose,
        bits: GirlBitmaps,
    ) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-GirlArt.FRAME_LEFT, -GirlArt.FRAME_TOP)
        baseMesh.draw(canvas, bits.base, pose.baseMesh)
        // the face in the base mesh already turns this much, so everything on it turns along
        canvas.save()
        canvas.rotateAround(pose.headDeg, GirlArt.NECK_X, GirlArt.NECK_Y)
        drawEye(canvas, bits.eyeLeft, GirlArt.EyeLeft, bits.eyeLeftShut, GirlArt.EyeLeftShut, GirlArt.EyeLeftLid, pose.eyesOpen)
        drawEye(canvas, bits.eyeRight, GirlArt.EyeRight, bits.eyeRightShut, GirlArt.EyeRightShut, GirlArt.EyeRightLid, pose.eyesOpen)
        drawMouth(canvas, bits.mouth, pose.mouthOpen)
        hairMesh.draw(canvas, bits.hair, pose.hairMesh)
        canvas.restore()
        canvas.restore()
    }

    fun drawHand(
        canvas: Canvas,
        scale: Float,
        pose: GirlPose,
        bits: GirlBitmaps,
    ) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-GirlArt.FRAME_LEFT, -GirlArt.FRAME_TOP)
        // the motion strokes sit on the sleeve, so they follow the hand only halfway
        canvas.save()
        canvas.translate(pose.phoneX * 0.5f, pose.phoneY * 0.5f)
        drawLayer(canvas, bits.motionLines, GirlArt.MotionLines, 1f)
        canvas.restore()
        canvas.translate(pose.phoneX, pose.phoneY)
        canvas.rotateAround(pose.phoneDeg, GirlArt.WRIST_X, GirlArt.WRIST_Y)
        drawLayer(canvas, bits.hand, GirlArt.Hand, 1f)
        canvas.restore()
    }

    // the open eye squashes toward its lid as it fades into the shut lash curve, so a blink never pops
    private fun drawEye(
        canvas: Canvas,
        open: ImageBitmap,
        openLayer: GirlLayer,
        shut: ImageBitmap,
        shutLayer: GirlLayer,
        lidY: Float,
        amount: Float,
    ) {
        val shown = ((amount - 0.12f) / 0.33f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        if (shown > 0f) {
            canvas.save()
            canvas.scaleAround(1f, 0.15f + 0.85f * amount, openLayer.x + openLayer.w / 2f, lidY)
            drawLayer(canvas, open, openLayer, shown)
            canvas.restore()
        }
        if (shown < 1f) drawLayer(canvas, shut, shutLayer, 1f - shown)
    }

    // the mouth eases from its top lip and widens a hair as it closes, like a soft breath
    private fun drawMouth(
        canvas: Canvas,
        mouth: ImageBitmap,
        open: Float,
    ) {
        val layer = GirlArt.Mouth
        canvas.save()
        canvas.scaleAround(1f + 0.05f * (1f - open), open, layer.x + layer.w / 2f, GirlArt.MouthTop)
        drawLayer(canvas, mouth, layer, 1f)
        canvas.restore()
    }

    private fun drawLayer(
        canvas: Canvas,
        image: ImageBitmap,
        layer: GirlLayer,
        alpha: Float,
    ) {
        if (alpha <= 0f) return
        paint.alpha = alpha.coerceAtMost(1f)
        // placed in art units through a scale, so a fraction of a pixel there is far less than one on screen
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.scale(layer.w / image.width, layer.h / image.height)
        canvas.drawImageRect(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(image.width, image.height), paint)
        canvas.restore()
        paint.alpha = 1f
    }
}

private fun Canvas.rotateAround(
    degrees: Float,
    x: Float,
    y: Float,
) {
    translate(x, y)
    rotate(degrees)
    translate(-x, -y)
}

private fun Canvas.scaleAround(
    sx: Float,
    sy: Float,
    x: Float,
    y: Float,
) {
    translate(x, y)
    scale(sx, sy)
    translate(-x, -y)
}
