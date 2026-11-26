package com.metrolist.shared.ui.settings

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

fun ColorScheme.settingsRowContainerColor(pressed: Boolean = false): Color {
    // Mochy's rows: near-black fields on a black page, a touch lighter while held
    if (isDark) return if (pressed) MochySettingsColors.FieldFocused else MochySettingsColors.Field
    val container = if (pressed) surfaceContainerHighest else surfaceContainerHigh
    // Pure black collapses container tokens; tonal elevation retains the theme's surface tint.
    return if (container == background) surfaceColorAtElevation(if (pressed) 12.dp else 6.dp) else container
}

fun ColorScheme.settingsRowOutlineColor(): Color = if (isDark) Color.Transparent else onSurface.copy(alpha = 0.07f)

/** Group headings: Mochy titles its sections in plain white. */
fun ColorScheme.settingsHeadingColor(): Color = if (isDark) Color.White else primary

/** The page behind settings rows: Mochy's pure black in the dark theme. */
fun ColorScheme.settingsPageColor(): Color = if (isDark) MochySettingsColors.Page else surface
