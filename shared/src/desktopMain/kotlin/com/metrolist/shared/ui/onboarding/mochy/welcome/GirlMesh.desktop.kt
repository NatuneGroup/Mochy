// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// a picture bent over a grid of points on Skia: the grid's cells as triangle pairs, the picture as their shader

package com.metrolist.shared.ui.onboarding.mochy.welcome

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

// Skia scales a large picture cheaply on the GPU here, so the art is kept at its own size
internal actual fun sizedGirlImage(
    bytes: ByteArray,
    width: Int,
    height: Int,
): ImageBitmap = bytes.decodeToImageBitmap()

internal actual class GirlMesh actual constructor(
    private val cols: Int,
    private val rows: Int,
) {
    private val perRow = cols + 1
    private val texs = FloatArray(perRow * (rows + 1) * 2)
    private var texsFor: ImageBitmap? = null
    private val paint = Paint().apply { isAntiAlias = true }

    // two triangles per cell, in drawBitmapMesh's order
    private val indices =
        ShortArray(cols * rows * 6).also { out ->
            var at = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val topLeft = row * perRow + col
                    val bottomLeft = topLeft + perRow
                    out[at++] = topLeft.toShort()
                    out[at++] = (topLeft + 1).toShort()
                    out[at++] = bottomLeft.toShort()
                    out[at++] = (topLeft + 1).toShort()
                    out[at++] = (bottomLeft + 1).toShort()
                    out[at++] = bottomLeft.toShort()
                }
            }
        }

    actual fun draw(
        canvas: Canvas,
        image: ImageBitmap,
        verts: FloatArray,
    ) {
        if (texsFor !== image) useImage(image)
        canvas.nativeCanvas.drawVertices(VertexMode.TRIANGLES, verts, null, texs, indices, BlendMode.MODULATE, paint)
    }

    // where each grid point reads the picture, spread evenly over it
    private fun useImage(image: ImageBitmap) {
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        var at = 0
        for (row in 0..rows) {
            val y = if (row == rows) height else height * row / rows
            for (col in 0..cols) {
                texs[at++] = if (col == cols) width else width * col / cols
                texs[at++] = y
            }
        }
        val skia = Image.makeFromBitmap(image.asSkiaBitmap())
        paint.shader = skia.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null)
        texsFor = image
    }
}
