/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.metrolist.android.utils.AppFontManager
import com.metrolist.android.utils.QuickFontManager
import com.metrolist.shared.settings.FontOption
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.MetrolistTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun getAppTypography(): Typography {
    val context = LocalContext.current
    val appFont = Settings.shared.appFont
    val customFontFile = Settings.shared.androidCustomFontFile
    val activeFontId = Settings.shared.androidActiveFontId
    val font by produceState<FontFamily>(FontFamily.Default, appFont, customFontFile, activeFontId) {
        value = withContext(Dispatchers.IO) { resolveFontFamily(context, appFont, customFontFile, activeFontId) }
    }
    return MetrolistTypography(fontFamilyOverride = font.takeIf { it != FontFamily.Default })
}

private fun resolveFontFamily(
    context: Context,
    appFont: FontOption,
    customFontFile: String,
    activeFontId: String,
): FontFamily {
    when (activeFontId) {
        QuickFontManager.SYSTEM_ID -> {
            return FontFamily.Default
        }

        QuickFontManager.CUSTOM_ID -> {
            return AppFontManager.loadFontFamily(context, customFontFile)
        }

        else -> {
            if (QuickFontManager.isSupportedId(activeFontId)) {
                return QuickFontManager.loadFontFamily(context, activeFontId)
            }
        }
    }

    if (activeFontId.isNotEmpty()) return FontFamily.Default

    if (appFont == FontOption.GOOGLE_SANS) {
        return QuickFontManager.loadFontFamily(context, QuickFontManager.GOOGLE_SANS_ID)
    }

    if (customFontFile.isNotBlank()) {
        val customFont = AppFontManager.loadFontFamily(context, customFontFile)
        if (customFont != FontFamily.Default) return customFont
    }

    return FontFamily.Default
}
