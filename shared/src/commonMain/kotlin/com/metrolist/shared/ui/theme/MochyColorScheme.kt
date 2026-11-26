// Adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal fun mochyColorScheme(isDark: Boolean): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = Color(0xFFE0E0E0),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF333333),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFFB0B0B0),
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF2C2C2C),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFFC6C6C6),
            onTertiary = Color.Black,
            tertiaryContainer = Color(0xFF303030),
            onTertiaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color.White,
            surfaceTint = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF242424),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0C0C0C),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF202020),
            surfaceContainerHighest = Color(0xFF282828),
            outline = Color(0xFF858585),
            outlineVariant = Color(0xFF363636),
            inverseSurface = Color(0xFFE5E5E5),
            inverseOnSurface = Color(0xFF181818),
            inversePrimary = Color(0xFF333333),
        )
    } else {
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0E0),
            onPrimaryContainer = Color.Black,
            secondary = Color(0xFF333333),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCCCCC),
            onSecondaryContainer = Color.Black,
            tertiary = Color(0xFF515151),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFDCDCDC),
            onTertiaryContainer = Color.Black,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF454545),
            surfaceTint = Color.White,
            surfaceDim = Color(0xFFD5D5D5),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF2F2F2),
            surfaceContainerHigh = Color(0xFFE9E9E9),
            surfaceContainerHighest = Color(0xFFE0E0E0),
            outline = Color(0xFF747474),
            outlineVariant = Color(0xFFC8C8C8),
            inverseSurface = Color(0xFF303030),
            inverseOnSurface = Color(0xFFF5F5F5),
            inversePrimary = Color(0xFFE0E0E0),
        )
    }
