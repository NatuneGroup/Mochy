// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Settings on a wide Mochy window: the list at the left, the page it opened beside it, a hairline between the two.
// Sizes and the placeholder follow the reference's SettingsPane.kt.

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.generated.resources.mochy_pane_hint
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// the list's share of the window, clamped the way the reference's SettingsListPane clamps it
private fun settingsPaneListWidth(windowWidth: Dp): Dp = (windowWidth * 0.38f).coerceIn(300.dp, 400.dp)

/**
 * The wide layout. [listSelectedPageId] is the page the list highlights (the one the stack was opened from);
 * [detailPageId] is the page shown on the right, or null for the placeholder. The pages themselves come from the
 * caller's page stack through [detail], so navigation stays in one place.
 */
@Composable
internal fun MochySettingsPane(
    registry: SettingsUiRegistry,
    host: SharedSettingsHost,
    listSelectedPageId: SettingsPageId?,
    detailPageId: SettingsPageId?,
    onPageSelected: (SettingsPageId) -> Unit,
    onSearchResultSelected: (SettingsPageId, String?) -> Unit,
    detail: @Composable (SettingsPageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MochySettingsColors.Page)) {
        val listWidth = settingsPaneListWidth(this.maxWidth)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                MochySettingsNavigationPane(
                    registry = registry,
                    host = host,
                    selectedPageId = listSelectedPageId,
                    onPageSelected = onPageSelected,
                    onSearchResultSelected = onSearchResultSelected,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(0.8.dp)
                        .fillMaxHeight()
                        .background(MochySettingsColors.Divider),
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (detailPageId == null) {
                    SettingsPanePlaceholder()
                } else {
                    detail(detailPageId)
                }
            }
        }
    }
}

// the space beside the list before a page is picked
@Composable
private fun SettingsPanePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(MochySettingsColors.Page),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.mochy_mark),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(Res.string.mochy_pane_hint),
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
