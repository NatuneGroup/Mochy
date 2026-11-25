package com.metrolist.shared.ui.player.mochy

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.metrolist.shared.ui.theme.currentAppLocaleList
import com.metrolist.shared.ui.theme.localeAwareSystemFontFamily
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.theme.rememberMochyLook

@Composable
fun flagshipFontFamily(): FontFamily =
    if (rememberMochyLook().font == "system") {
        localeAwareSystemFontFamily(currentAppLocaleList()[0].toLanguageTag())
    } else {
        mochyInterFontFamily()
    }
