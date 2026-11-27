// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's settings kit: the grouped card a page's section sits in, its rows, the pill that gathers a parent row
// with its sub-options, the section title, and the search field at the top of each page.

package com.metrolist.shared.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_set_navigate_next
import com.metrolist.shared.generated.resources.settings_clear_search
import com.metrolist.shared.generated.resources.settings_search_placeholder
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.onboarding.mochy.AppSwitch
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// the grouped card, the pill a parent and its open sub-options share, and the row icon's circle
private val GroupColor = Color(0xFF1C1C1E)
private val SubOptionPillColor = Color(0xFF282830)
private val IconCircleColor = Color(0xFFE5E5EA)
private val IconInkColor = Color(0xFF1C1C1E)
private const val ROW_ANIMATION_MS = 300

/** The row a search result or related link points at; it scrolls into view and flashes once. */
internal val LocalMochySettingsHighlight = staticCompositionLocalOf<MochySettingsHighlight?> { null }

internal class MochySettingsHighlight(
    initial: String?,
) {
    var pending by mutableStateOf(initial)
    var active by mutableStateOf<String?>(null)
}

data class MochySettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val descriptionText: String? = null,
    /** The whole description, shown when the row is held; [descriptionText] is the short line. */
    val detail: String? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val tintIcon: Boolean = true,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val isSubOption: Boolean = false,
    /** What search results and related links point at to flash this row. */
    val highlightKey: String? = null,
    // the title as shown in the app's language, so a page's own search finds it
    val searchText: String? = null,
    val onClick: (() -> Unit)? = null,
)

// a section's title (if it has one) and its grouped rows, left out whole when the search hides every row
@Composable
internal fun MochySettingsSection(
    title: String?,
    items: List<MochySettingsItem>,
) {
    if (items.isEmpty()) return
    if (title != null) {
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
    }
    MochySettingsGroup(items)
    Spacer(Modifier.height(18.dp))
}

internal fun List<MochySettingsItem>.matching(query: String): List<MochySettingsItem> {
    if (query.isBlank()) return this
    return filter {
        it.searchText?.contains(query, ignoreCase = true) == true ||
            it.descriptionText?.contains(query, ignoreCase = true) == true ||
            it.detail?.contains(query, ignoreCase = true) == true
    }
}

// a row title with its value on the right, for the rows with a slider under them
@Composable
internal fun MochyValueTitle(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = value,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.70f),
        )
    }
}

// one switch row: subOptionOf tucks it under the parent row before it, shown while that parent is on
internal fun mochySwitchItem(
    key: String,
    title: String,
    description: String?,
    icon: Painter?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
    subOptionOf: Boolean? = null,
    visible: Boolean = true,
    enabled: Boolean = true,
) = MochySettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    detail = detail,
    icon = icon,
    isVisible = visible && subOptionOf != false,
    isSubOption = subOptionOf != null,
    enabled = enabled,
    onClick = { onCheckedChange(!checked) },
    trailingContent = {
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled && subOptionOf != false)
    },
)

// a row that opens another page, with the little arrow on the right
internal fun mochyLinkItem(
    key: String,
    title: String,
    description: String?,
    icon: Painter?,
    onClick: () -> Unit,
    visible: Boolean = true,
    isSubOption: Boolean = false,
    value: String? = null,
) = MochySettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    icon = icon,
    isVisible = visible,
    isSubOption = isSubOption,
    onClick = onClick,
    trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    text = value,
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            MochyNextArrow()
        }
    },
)

@Composable
internal fun MochyNextArrow() {
    Icon(
        painter = painterResource(Res.drawable.mochy_set_navigate_next),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.size(20.dp),
    )
}

private class ItemCluster(
    val parent: MochySettingsItem,
    val subs: MutableList<MochySettingsItem> = mutableListOf(),
)

private fun clusterItems(items: List<MochySettingsItem>): List<ItemCluster> {
    val clusters = mutableListOf<ItemCluster>()
    items.forEach { item ->
        if (item.isSubOption && clusters.isNotEmpty()) {
            clusters.last().subs += item
        } else {
            clusters += ItemCluster(item)
        }
    }
    return clusters
}

// the reference's grouped style: one 28dp card per section, each parent row and its sub-options a cluster in it
@Composable
internal fun MochySettingsGroup(items: List<MochySettingsItem>) {
    val clusters = clusterItems(items)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = GroupColor,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            clusters.forEach { cluster ->
                AnimatedVisibility(
                    visible = cluster.parent.isVisible,
                    enter = expandVertically(tween(ROW_ANIMATION_MS)) + fadeIn(tween(ROW_ANIMATION_MS)),
                    exit = shrinkVertically(tween(ROW_ANIMATION_MS)) + fadeOut(tween(ROW_ANIMATION_MS)),
                ) {
                    ClusterRows(cluster)
                }
            }
        }
    }
}

@Composable
private fun ClusterRows(cluster: ItemCluster) {
    val hasVisibleSubs = cluster.subs.any { it.isVisible }
    val pillAlpha by animateFloatAsState(
        targetValue = if (hasVisibleSubs) 1f else 0f,
        animationSpec = tween(ROW_ANIMATION_MS),
        label = "pillAlpha",
    )
    val pillInset by animateDpAsState(
        targetValue = if (hasVisibleSubs) 6.dp else 0.dp,
        animationSpec = tween(ROW_ANIMATION_MS),
        label = "pillInset",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = pillInset, vertical = pillInset / 2)
                .clip(RoundedCornerShape(22.dp))
                .background(SubOptionPillColor.copy(alpha = SubOptionPillColor.alpha * pillAlpha)),
    ) {
        Column {
            MochySettingsItemRow(cluster.parent)
            cluster.subs.forEach { sub ->
                AnimatedVisibility(
                    visible = sub.isVisible,
                    enter = expandVertically(tween(ROW_ANIMATION_MS)) + fadeIn(tween(ROW_ANIMATION_MS)),
                    exit = shrinkVertically(tween(ROW_ANIMATION_MS)) + fadeOut(tween(ROW_ANIMATION_MS)),
                ) {
                    MochySettingsItemRow(sub)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MochySettingsItemRow(item: MochySettingsItem) {
    // app behaviour can hide the line under each title, holding the row still shows it
    val hideDescriptions = Settings.shared.hideSettingsDescriptions
    val requester = remember { BringIntoViewRequester() }
    val highlight = LocalMochySettingsHighlight.current
    val key = item.highlightKey
    LaunchedEffect(key, highlight) {
        if (key == null || highlight == null || highlight.pending != key) return@LaunchedEffect
        highlight.pending = null
        highlight.active = key
        // let the page finish opening first, or it scrolls while still sliding in
        delay(320)
        requester.bringIntoView()
        delay(2400)
        if (highlight.active == key) highlight.active = null
    }
    val flashing = key != null && highlight?.active == key
    val highlightAlpha by animateFloatAsState(
        targetValue = if (flashing) 1f else 0f,
        animationSpec = tween(450),
        label = "settingsHighlight",
    )
    val highlightColor = MaterialTheme.colorScheme.primary
    var showDetail by remember { mutableStateOf(false) }
    val fullText = item.detail ?: item.descriptionText

    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(requester)
                    .drawBehind {
                        if (highlightAlpha > 0.01f) drawRect(color = highlightColor.copy(alpha = 0.22f * highlightAlpha))
                    }.combinedClickable(
                        enabled = item.enabled && (item.onClick != null || fullText != null),
                        onClick = { item.onClick?.invoke() },
                        // holding a row shows its whole description, since the line under it is cut to one
                        onLongClick = fullText?.let { { showDetail = true } },
                    ).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.icon?.let { icon ->
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(IconCircleColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = if (item.enabled) IconInkColor else IconInkColor.copy(alpha = 0.38f),
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                val titleColor =
                    if (item.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f,
                        )
                    }
                ProvideTextStyle(
                    MaterialTheme.typography.titleSmall.copy(color = titleColor, fontFamily = mochyInterFontFamily()),
                ) {
                    item.title()
                }
                val descColor =
                    if (item.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f,
                        )
                    }
                if (item.descriptionText != null && !hideDescriptions) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = item.descriptionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = descColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.description?.let { desc ->
                    Spacer(Modifier.height(2.dp))
                    ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = descColor)) { desc() }
                }
            }
            item.trailingContent?.let { trailing ->
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (showDetail && fullText != null) {
            LaunchedEffect(Unit) {
                delay(2400)
                showDetail = false
            }
            Popup(alignment = Alignment.TopCenter, onDismissRequest = { showDetail = false }) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GroupColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = fullText,
                        fontFamily = mochyInterFontFamily(),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// the field at the top of each page that narrows its rows down
@Composable
internal fun MochySubSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(GroupColor)
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle =
                    TextStyle(
                        fontFamily = mochyInterFontFamily(),
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Normal,
                    ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_search_placeholder),
                            fontFamily = mochyInterFontFamily(),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    MetrolistIcons.Default.Close,
                    contentDescription = stringResource(Res.string.settings_clear_search),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onQueryChange("") },
                )
            }
        }
    }
}
