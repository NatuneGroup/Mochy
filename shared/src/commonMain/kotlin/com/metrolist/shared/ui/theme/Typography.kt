/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocaleList
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_inter_bold
import com.metrolist.shared.generated.resources.mochy_inter_medium
import com.metrolist.shared.generated.resources.mochy_inter_regular
import com.metrolist.shared.generated.resources.mochy_inter_semibold
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.resolveAppLanguageTag
import com.metrolist.shared.state.GlobalYouTube
import org.jetbrains.compose.resources.Font

@Composable
fun currentAppLocaleList(): LocaleList = resolveAppLocaleList(Settings.shared.appLanguage, LocalLocaleList.current)

internal fun resolveAppLocaleList(
    configuredLanguage: String,
    systemLocales: LocaleList,
): LocaleList =
    if (configuredLanguage.isBlank() || configuredLanguage.equals(GlobalYouTube.SYSTEM, ignoreCase = true)) {
        systemLocales
    } else {
        LocaleList(
            resolveAppLanguageTag(configuredLanguage, systemLocales.map { it.toLanguageTag() }),
        )
    }

@Composable
expect fun localeAwareSystemFontFamily(languageTag: String): FontFamily

@Composable
fun mochyInterFontFamily(): FontFamily =
    FontFamily(
        Font(Res.font.mochy_inter_regular, FontWeight.Normal),
        Font(Res.font.mochy_inter_medium, FontWeight.Medium),
        Font(Res.font.mochy_inter_semibold, FontWeight.SemiBold),
        Font(Res.font.mochy_inter_bold, FontWeight.Bold),
    )

@Composable
fun MetrolistTypography(
    localeList: LocaleList = currentAppLocaleList(),
    fontFamilyOverride: FontFamily? = null,
): Typography {
    val systemFont = localeAwareSystemFontFamily(localeList[0].toLanguageTag())
    val useMochyInter = fontFamilyOverride == null && systemFont == FontFamily.Default
    val font =
        when {
            fontFamilyOverride != null -> fontFamilyOverride
            useMochyInter -> mochyInterFontFamily()
            else -> systemFont
        }

    // Apply Mochy Music's Inter weights and -0.05em tracking only when Inter is active so
    // non-Latin system fonts and custom fonts keep readable metrics.
    if (useMochyInter) {
        return Typography(
            fontFamily = font,
            displayLarge =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    localeList = localeList,
                    fontSize = 57.sp,
                    lineHeight = 64.sp,
                    letterSpacing = (-0.05).em,
                ),
            displayMedium =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 45.sp,
                    localeList = localeList,
                    lineHeight = 52.sp,
                    letterSpacing = (-0.05).em,
                ),
            displaySmall =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 36.sp,
                    localeList = localeList,
                    lineHeight = 44.sp,
                    letterSpacing = (-0.05).em,
                ),
            headlineLarge =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                    localeList = localeList,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.05).em,
                ),
            headlineMedium =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    localeList = localeList,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.05).em,
                ),
            headlineSmall =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    localeList = localeList,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.05).em,
                ),
            titleLarge =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    localeList = localeList,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    letterSpacing = (-0.05).em,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    localeList = localeList,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.05).em,
                ),
            titleSmall =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    localeList = localeList,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.05).em,
                ),
            bodyLarge =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    localeList = localeList,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.05).em,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    localeList = localeList,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.05).em,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    localeList = localeList,
                    lineHeight = 16.sp,
                    letterSpacing = (-0.05).em,
                ),
            labelLarge =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    localeList = localeList,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.05).em,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    localeList = localeList,
                    lineHeight = 16.sp,
                    letterSpacing = (-0.05).em,
                ),
            labelSmall =
                TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    localeList = localeList,
                    lineHeight = 16.sp,
                    letterSpacing = (-0.05).em,
                ),
        )
    }

    return Typography(
        fontFamily = font,
        displayLarge =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                localeList = localeList,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                localeList = localeList,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                localeList = localeList,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                localeList = localeList,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                localeList = localeList,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                localeList = localeList,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                localeList = localeList,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                localeList = localeList,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                localeList = localeList,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                localeList = localeList,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                localeList = localeList,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                localeList = localeList,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                localeList = localeList,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                localeList = localeList,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                localeList = localeList,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
}
