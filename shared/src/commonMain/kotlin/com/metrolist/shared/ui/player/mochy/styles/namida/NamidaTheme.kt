// Ported from Sora (github.com/shizenworks/sora), its namida player style after namida (github.com/namidaco/namida). Licensed under GPL-3.0.

// namida player style — the typeface, inks and colour math, ported 1:1 from namida:
// its LexendDeca static weights (assets/fonts/LexendDeca, SIL OFL), its display text
// styles (core/themes.dart) and its panel/played colour blends (miniplayer_base.dart's
// _ThemedMiniplayerPanel and WaveformMiniplayer).

@file:OptIn(ExperimentalTextApi::class)

package com.metrolist.shared.ui.player.mochy.styles.namida

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.lexend_deca_bold
import com.metrolist.shared.generated.resources.lexend_deca_light
import com.metrolist.shared.generated.resources.lexend_deca_medium
import com.metrolist.shared.generated.resources.lexend_deca_regular
import com.metrolist.shared.generated.resources.lexend_deca_semibold
import org.jetbrains.compose.resources.Font

/**
 * namida's typeface: the exact LexendDeca static weights its pubspec registers
 * (Light..Bold), copied verbatim into the font resources.
 */
val NamidaFontFamily: FontFamily
    @Composable get() =
        FontFamily(
            Font(Res.font.lexend_deca_light, weight = FontWeight.Light),
            Font(Res.font.lexend_deca_regular, weight = FontWeight.Normal),
            Font(Res.font.lexend_deca_medium, weight = FontWeight.Medium),
            Font(Res.font.lexend_deca_semibold, weight = FontWeight.SemiBold),
            Font(Res.font.lexend_deca_bold, weight = FontWeight.Bold),
        )

/**
 * The palette namida's player draws with, all of it derived the way namida derives
 * it: the current colour (the artwork's accent) blends onto the surface for the
 * panel, onto onSurface for the played waveform bars, and the inks are namida's
 * display text colours (white at 0.82/0.71/0.67 in the dark, black at
 * 0.63/0.59/0.47 in the light).
 */
class NamidaPalette(
    val currentColor: Color,
    val scaffold: Color,
    val onSurface: Color,
    val dark: Boolean,
) {
    // -- the inks (core/themes.dart displayLarge/Medium/Small) --
    val textLarge: Color = if (dark) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.63f)
    val textMedium: Color = if (dark) Color.White.copy(alpha = 0.71f) else Color.Black.copy(alpha = 0.59f)
    val textSmall: Color = if (dark) Color.White.copy(alpha = 0.67f) else Color.Black.copy(alpha = 0.47f)

    // -- the M3 roles the player reads (secondaryContainer for the menu chip) --
    val onSecondaryContainer: Color = if (dark) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.56f)
    val secondaryContainer: Color = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    val secondary: Color = if (dark) Color.White.copy(alpha = 0.71f) else Color.Black.copy(alpha = 0.54f)

    // -- the screen behind the panel: the cover's own tone, deep enough for the white top row,
    // easing down into the panel's first colour so the two read as one surface --
    val backdropTop: Color = lerp(scaffold, currentColor, 0.45f)

    // -- the panel (miniplayer_base.dart _ThemedMiniplayerPanel) --
    val panelBackground: Color = scaffold
    val panelGradientTop: Color =
        onSurface.copy(alpha = 0.39f).compositeOver(currentColor.copy(alpha = currentColor.alpha))
    val panelGradientBottom: Color =
        onSurface.copy(alpha = 0.16f).compositeOver(currentColor.copy(alpha = currentColor.alpha))

    // -- the play/pause button (miniplayer_base.dart _PlayPauseButton) --
    val playIcon: Color = Color.White.copy(alpha = 0.71f)
    val playGradient: List<Color> =
        listOf(
            currentColor,
            Color.Gray.copy(alpha = 1f).let { grey ->
                currentColor.copy(alpha = 0.78f).compositeOver(grey)
            },
        )
    val playShadow: Color = currentColor.copy(alpha = 0.63f)

    // -- the waveform (ui/widgets/waveform.dart) --
    val waveBehind: Color = onSurface.copy(alpha = 0.16f)
    val waveFront: Color = onSurface.copy(alpha = 0.43f)
    val wavePlayed: List<Color> =
        listOf(
            currentColor.copy(alpha = 0.71f).compositeOver(onSurface).copy(alpha = 0.43f),
            currentColor.copy(alpha = 0.55f).compositeOver(onSurface).copy(alpha = 0.43f),
        )

    /** the queue page's accent card gradient (NamidaGradients.accentCard, opacity 0.5) */
    val accentCard: Color = lerp(scaffold, currentColor, 0.5f)

    companion object {
        /**
         * namida's CurrentColor: the artwork's accent, pushed away from the extremes
         * the way namida's palette does (a quiet saturation/brightness normalisation
         * so the panel never turns neon or mud).
         */
        fun fromArtwork(
            seed: Color,
            scaffold: Color,
            dark: Boolean,
        ): NamidaPalette {
            val hsv = seed.toHsv()
            hsv[1] = (hsv[1] * 0.82f + 0.08f).coerceIn(0.18f, 0.85f)
            hsv[2] = hsv[2].coerceIn(0.38f, 0.86f)
            val color = Color.hsv(hsv[0], hsv[1], hsv[2])
            // the blend base: namida's dark themes blend onto white, its light themes onto black
            val onSurface = if (dark) Color.White else Color.Black
            return NamidaPalette(color, scaffold, onSurface, dark)
        }
    }
}

val LocalNamidaPalette =
    staticCompositionLocalOf {
        NamidaPalette(Color(0xFF5468A0), Color(0xFF16161C), Color.White, dark = true)
    }

@Composable
fun rememberNamidaPalette(seed: Color): NamidaPalette {
    // the page colour, solid: the theme's background can be see-through over the app's own backdrop, and namida's
    // panel tones are mixed from it
    val scaffold = MaterialTheme.colorScheme.background.let { if (it.alpha < 1f) Color.Black else it }
    val dark = scaffold.luminanceAwareDark()
    return remember(seed, scaffold, dark) { NamidaPalette.fromArtwork(seed, scaffold, dark) }
}

private fun Color.luminanceAwareDark(): Boolean {
    val r = red * 255
    val g = green * 255
    val b = blue * 255
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f < 0.5f
}

// hue in degrees, saturation and value from 0 to 1, as Android's colorToHSV gives them
private fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue =
        when {
            delta == 0f -> 0f
            max == red -> 60f * (((green - blue) / delta) % 6f)
            max == green -> 60f * (((blue - red) / delta) + 2f)
            else -> 60f * (((red - green) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}
