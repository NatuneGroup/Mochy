// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the layout engine, ported 1:1 from namida's
// packages/miniplayer_base.dart (_MiniplayerLayout.update), the exact geometry
// math namida's miniplayer runs per frame: the same interpolation helpers, the
// same offsets, the same panel height and radii, expressed in Kotlin.
//
// p is namida's expansion parameter: 1 = expanded player, 2 = queue page (the
// queue slides up over the player), 3 = its overshoot. Values in between are
// the drag/animation itself, so every element rides the same numbers namida's
// elements ride.
//
// namida lays its whole miniplayer out in a virtual box and scales it down to
// fit the real screen (NamidaUIScale's sqrt ramp below 400dp shortest side,
// then the controller's own panelScale against a 360x540 reference); every dp
// and sp the style draws goes through [NamidaScale] so the port measures the
// same numbers namida measures on the same device.

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** namida's inverseAboveOne (miniplayer_raw.dart): mirrors n back below 1. */
internal fun inverseAboveOne(n: Double): Double = if (n > 1) 2 - n else n

/** namida's velpy (miniplayer_raw.dart): a + (b - a) * c. */
internal fun velpy(
    a: Double,
    b: Double,
    c: Double,
): Double = c * (b - a) + a

internal fun lerp(
    a: Double,
    b: Double,
    t: Double,
): Double = a * (1.0 - t) + b * t

/** namida's expanded-player design height (_perfectHeight). */
internal const val NAMIDA_PERFECT_HEIGHT = 540.0

/** the queue bottom row (Dimensions.kQueueBottomRowHeight). */
internal val NamidaQueueBottomRowHeight = 48.dp

/** the queue page's rounded top (32.0 in miniplayer_base). */
internal val NamidaQueueTopRadius = 32.dp

/** the artwork corner radius (14.0.multipliedRadius in miniplayer_base). */
internal val NamidaArtworkRadius = 14.dp

/** one queue tile: 72 tall with a 64 thumbnail, extent 90 (Dimensions). */
internal val NamidaQueueTileHeight = 72.dp
internal val NamidaQueueThumbnailSize = 64.dp
internal val NamidaQueueItemExtent = 90.dp

/**
 * namida's own double scale, resolved exactly like the app does it:
 * NamidaUIScale._resolve (sqrt of the shortest side over a 400 reference,
 * clamped 0.7..1.0) and MiniPlayerController._resolvePanelScale (the virtual
 * box against a 360x540 reference, clamped 0.7..1.0). Everything the layout
 * outputs is virtual dp; everything the style draws comes back through
 * [dp] and [scaled] so the port lands where namida lands.
 */
internal class NamidaScale(
    screenWidthDp: Double,
    screenHeightDp: Double,
) {
    val uiScale: Double
    val panelScale: Double
    val total: Double

    /** namida's virtual screen (the MediaQuery the miniplayer subtree sees). */
    val virtualWidth: Double
    val virtualHeight: Double

    init {
        val shortest = min(screenWidthDp, screenHeightDp)
        uiScale = if (shortest > 0) sqrt(shortest / 400.0).coerceIn(0.7, 1.0) else 1.0
        virtualWidth = screenWidthDp / uiScale
        virtualHeight = screenHeightDp / uiScale
        panelScale =
            if (virtualWidth > 0 && virtualHeight > 0) {
                min(virtualWidth / 360.0, virtualHeight / 540.0).coerceIn(0.7, 1.0)
            } else {
                1.0
            }
        total = uiScale * panelScale
    }

    /** a virtual-dp measurement from namida's math, as real dp on this screen. */
    fun dp(value: Double): Dp = (value * total).dp

    /** one of namida's absolute constants (an icon size, a margin), as real dp here. */
    fun scaled(value: Dp): Dp = value * total.toFloat()

    /** a font size, which namida's scale boxes scale the same as everything else. */
    fun sp(value: Double) =
        (value * total).let {
            androidx.compose.ui.unit
                .TextUnit(it.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
        }

    /** a real-dp inset of this screen, as the virtual dp namida's math wants. */
    fun virtual(value: Double): Double = value / total
}

/** the play/pause metrics (_PlayPauseMetrics), at any p. */
internal object NamidaPlayPauseMetrics {
    fun iconSize(p: Double): Double {
        val cp = p.coerceIn(0.0, 1.0)
        val rp = inverseAboveOne(p)
        val rcp = rp.coerceIn(0.0, 1.0)
        return (velpy(a = 60.0 * 0.5, b = 80.0 * 0.5, c = rp) - 8) + 8 * cp * rcp
    }

    fun extraPadding(iconSize: Double): Double = min(iconSize * 0.5, 14.0)

    fun boxSize(iconSize: Double): Double = iconSize + extraPadding(iconSize) * 2

    const val MAX_ICON_SIZE = 40.0
    val MaxBoxSize: Double get() = boxSize(MAX_ICON_SIZE)
}

/** one frame of namida's miniplayer geometry, all values in dp-as-double. */
internal class NamidaLayout {
    var p = 0.0
        private set
    var cp = 0.0
        private set
    var icp = 1.0
        private set
    var bp = 0.0
        private set
    var bcp = 0.0
        private set
    var qp = 0.0
        private set
    var qcp = 0.0
        private set

    var panelHeight = 0.0
        private set
    var panelTopRadius = 0.0
        private set
    var panelBottomRadius = 0.0
        private set
    var waveformYScale = 1.0
        private set
    var heightFactor = 1.0
        private set

    var vOffsetControls = 0.0
        private set
    var vOffsetWaveform = 0.0
        private set
    var vOffsetTrackInfo = 0.0
        private set
    var trackInfoBoxHeight = 0.0
        private set

    var nextPrevIconSize = 0.0
        private set
    var nextPrevIconPadding = 0.0
        private set
    var buttonsRightPadding = 0.0
        private set

    var imageWidth = 0.0
        private set
    var imageMaxWidth = 0.0
        private set
    var imageMaxHeight = 0.0
        private set
    var imagePaddingAll = 0.0
        private set
    var imagePaddingLeft = 0.0
        private set
    var imageBaseVOffset = 0.0
        private set
    var imageSpaceAbove = 0.0
        private set
    var imageSpaceInPanel = 0.0
        private set
    var imageCenterLeft = 0.0
        private set

    var topRowHeight = 0.0
        private set
    var maxQueueHeight = 0.0
        private set

    /**
     * inputs that don't change while dragging. [maxOffset] is namida's
     * controller.maxOffset MINUS the nav bar height, exactly as its update()
     * derives it: the panel adds the nav bar back, every other offset rides
     * the reduced one.
     */
    data class Inputs(
        val screenWidth: Double,
        val maxOffset: Double,
        val topInset: Double,
        val bottomInset: Double,
        val navBarHeight: Double,
        val imageAspectRatio: Double,
    )

    fun update(
        p: Double,
        inputs: Inputs,
    ) {
        val screenWidth = inputs.screenWidth
        val maxOffset = inputs.maxOffset
        val topInset = inputs.topInset
        val bottomInset = inputs.bottomInset
        val navBarHeight = inputs.navBarHeight

        val cp = p.coerceIn(0.0, 1.0)
        val icp = 1 - cp
        val rp = inverseAboveOne(p)
        val rcp = rp.coerceIn(0.0, 1.0)
        val qp = p.coerceIn(1.0, 3.0) - 1.0
        val qcp = qp.coerceIn(0.0, 1.0)
        // no bounce in the style port: bp rides rp as namida does with both bounces off
        val bp = rp
        val bcp = bp.coerceIn(0.0, 1.0)

        val waveformYScale = if (maxOffset < NAMIDA_PERFECT_HEIGHT) maxOffset / NAMIDA_PERFECT_HEIGHT * 0.9 else 1.0

        val panelH = maxOffset + navBarHeight - (100.0 + topInset + 4.0) * qp
        val panelExtra = panelH / 2.4 - (100.0 + topInset + 4.0) * qp
        val panelFinal = panelH - panelExtra * (1 - qcp)

        val iconSize = NamidaPlayPauseMetrics.iconSize(p)
        val iconBoxSize = NamidaPlayPauseMetrics.boxSize(iconSize)

        val nextprevmultiplier = (inverseAboveOne(p - 2.0) + 3.0) * (1 - qp) - 1
        val nextPrevIconSize = 21.0 + 11.0 * nextprevmultiplier
        val nextPrevIconPadding = 8.0 + 4.0 * cp + 6.0 * nextprevmultiplier

        val totalButtonsSize = iconBoxSize + (nextPrevIconSize + nextPrevIconPadding * 2) * 2
        val buttonsRightPadding = cp * rcp * ((screenWidth - totalButtonsSize) / 2)

        val heightFactor = lerp(1.0, maxOffset / NAMIDA_PERFECT_HEIGHT, rp)

        val topRowHeight = 1.25 * (32.0 * heightFactor + NAMIDA_TOP_BOTTOM_MARGIN * 2) * cp
        val bottomRowHeight = topRowHeight
        val imageWidth = velpy(a = 82.0, b = 92.0, c = qp)

        val vOffsetExtras = (-(maxOffset - topInset - 100.0 - 12.0 * 2 + 4.0) * qp) - navBarHeight * cp
        val vOffsetExtrasAlt = (-(maxOffset - topInset - 100.0 + 8.0 * 2 - 4.0) * qp) - navBarHeight * cp
        val trackInfoBoxHeight = velpy(a = 58.0, b = 82.0, c = bcp)
        val vOffsetControls = vOffsetExtrasAlt - bottomRowHeight * bp
        var vOffsetWaveform = vOffsetControls - iconSize - 64.0 * waveformYScale / 2 - panelFinal * 0.026
        // namida's withMaximum: the waveform never rises past this cap mid-expansion
        vOffsetWaveform = min(vOffsetWaveform, -(maxOffset - bottomInset - topInset) * 0.2 * (1 - bcp))

        val vOffsetTrackInfo =
            lerp(
                lerp(
                    vOffsetExtras,
                    -maxOffset + imageWidth / 2 + topInset + 100.0 / 2 + 12.0 * heightFactor - navBarHeight * cp,
                    qp,
                ),
                // namida's withMaximum: the waveform-anchored position never rises past this cap
                min(vOffsetWaveform - 64.0 * waveformYScale, -(maxOffset - bottomInset - topInset) * 0.3),
                bcp,
            )
        var vOffsetImage = vOffsetTrackInfo - trackInfoBoxHeight * bcp - 16.0 * heightFactor * bcp + 6.0 * heightFactor * qp

        val imageMaxWidthPre = screenWidth - max(screenWidth * 0.2, 76.0)
        val imageMaxHeightPre = maxOffset + vOffsetImage - topRowHeight - topInset - 24.0 * heightFactor
        val spaceLeftInPanelAboveInfo = panelFinal + vOffsetTrackInfo - trackInfoBoxHeight

        val imagePaddingAll = 12.0 * (1 - bcp)
        val imagePaddingLeft = imagePaddingAll + 42.0 * bcp

        this.p = p
        this.cp = cp
        this.icp = icp
        this.bp = bp
        this.bcp = bcp
        this.qp = qp
        this.qcp = qcp
        this.panelHeight = velpy(a = 82.0, b = panelFinal, c = cp)
        this.panelTopRadius = NAMIDA_PANEL_BASE_RADIUS + 6.0 * p
        this.panelBottomRadius = (NAMIDA_PANEL_BASE_RADIUS * (1 - p * 10 + 9)).coerceIn(0.0, 1.0)
        this.waveformYScale = waveformYScale
        this.heightFactor = heightFactor
        this.vOffsetControls = vOffsetControls
        this.vOffsetWaveform = vOffsetWaveform
        this.vOffsetTrackInfo = vOffsetTrackInfo
        this.trackInfoBoxHeight = trackInfoBoxHeight
        this.nextPrevIconSize = nextPrevIconSize
        this.nextPrevIconPadding = nextPrevIconPadding
        this.buttonsRightPadding = buttonsRightPadding
        this.imageWidth = imageWidth
        this.imageMaxWidth = imageMaxWidthPre
        this.imageMaxHeight = imageMaxHeightPre
        this.imagePaddingAll = imagePaddingAll
        this.imagePaddingLeft = imagePaddingLeft
        this.imageBaseVOffset = vOffsetImage
        this.imageSpaceAbove = maxOffset + vOffsetImage - topInset - topRowHeight
        this.imageSpaceInPanel = spaceLeftInPanelAboveInfo
        this.imageCenterLeft = screenWidth / 2 - imagePaddingLeft
        this.topRowHeight = topRowHeight
        this.maxQueueHeight = maxOffset + navBarHeight - 100.0 - topInset - 12.0
    }

    /**
     * the artwork box for one card, following its aspect ratio (_ImageSlotLayout.update).
     * The returned [ImageSlot.leftOffset] is where the PICTURE lands (namida's
     * Align pads leftOffset by imagePaddingLeft before the constrained image
     * inside, which cancels the -imagePaddingLeft in imageCenterLeft: the
     * picture is centred).
     */
    fun imageSlot(aspectRatio: Double): ImageSlot {
        val bcp = bcp
        val collapsedWidth = imageWidth
        val widthBig = min(imageMaxWidth, imageMaxHeight * aspectRatio)
        val heightBig = widthBig / aspectRatio
        val boxWidth = velpy(collapsedWidth, widthBig, bcp)
        val boxHeight = velpy(collapsedWidth, heightBig, bcp)
        val spaceLeftAboveImage = imageSpaceAbove - boxHeight
        var vOffset = imageBaseVOffset
        if (spaceLeftAboveImage > 0) {
            vOffset -= min(imageSpaceInPanel * 0.5, spaceLeftAboveImage * 0.5) * bcp
        } else {
            vOffset += (-spaceLeftAboveImage / 2) * bcp
        }
        return ImageSlot(
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            leftOffset = (imageCenterLeft - boxWidth / 2) * bcp + imagePaddingLeft * bcp,
            vOffset = vOffset,
        )
    }

    data class ImageSlot(
        val boxWidth: Double,
        val boxHeight: Double,
        val leftOffset: Double,
        val vOffset: Double,
    ) {
        val width: Dp get() = boxWidth.dp
        val height: Dp get() = boxHeight.dp
        val left: Dp get() = leftOffset.dp
    }

    companion object {
        /** namida's _kTopBottomMargin. */
        const val NAMIDA_TOP_BOTTOM_MARGIN = 8.0

        /** namida's panelBaseRadius (20.0.multipliedRadius). */
        const val NAMIDA_PANEL_BASE_RADIUS = 20.0
    }
}
