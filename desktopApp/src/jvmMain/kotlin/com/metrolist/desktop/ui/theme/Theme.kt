/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.metrolist.desktop.state.app.UiState
import com.metrolist.desktop.utils.DesktopFontManager
import com.metrolist.desktop.utils.MatugenColors
import com.metrolist.shared.settings.FontOption
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.MetrolistTypography
import com.metrolist.shared.ui.theme.currentAppLocaleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.shared.ui.theme.MetrolistTheme as SharedMetrolistTheme

@Composable
private fun appFontFamilyOverride(languageTag: String): FontFamily? {
    val option = Settings.shared.appFont
    val revision = DesktopFontManager.revision
    val family by produceState<FontFamily?>(null, option, revision, languageTag) {
        value = null
        if (option != FontOption.SYSTEM) {
            value =
                withContext(Dispatchers.IO) {
                    // Compose does not pass TextStyle locales to Skia; keep the locale-matched system font as the fallback.
                    DesktopFontManager
                        .loadFontFamily(option)
                        ?.takeIf { DesktopFontManager.supportsLanguage(option, languageTag) }
                }
        }
    }
    return family
}

@Composable
fun AppTypography(): Typography {
    val localeList = currentAppLocaleList()
    val override = appFontFamilyOverride(localeList[0].toLanguageTag())
    return MetrolistTypography(localeList = localeList, fontFamilyOverride = override)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetrolistTheme(
    seedColor: Color = UiState.seedColor,
    pureBlack: Boolean = Settings.shared.isPureBlackTheme(),
    content: @Composable () -> Unit,
) {
    val isDark = Settings.shared.getCurrentThemeIsDark()
    val matugen = MatugenColors.palette

    SharedMetrolistTheme(
        seedColor = seedColor,
        darkTheme = isDark,
        pureBlack = pureBlack,
        colorScheme = matugen?.let { if (isDark) it.dark else it.light },
    ) {
        MaterialExpressiveTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = AppTypography(),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
