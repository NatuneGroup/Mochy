/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.android.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.android.LocalMiniPlayerBottomInset
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.beta_tag
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.components.singleLineMarquee
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.settings.LocalSettingsPageHasTopBar
import com.metrolist.shared.ui.settings.LocalSettingsPageTopPadding
import com.metrolist.shared.ui.settings.rememberSettingsPageScrollBehavior
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.tv.tvFocusHighlight
import com.metrolist.shared.ui.utils.segmentedGroupShape
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubScreen(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    persistentHeader: @Composable () -> Unit = {},
    contentScrollable: Boolean = true,
    compactTopBar: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = rememberSettingsPageScrollBehavior(compactTopBar)
    val miniPlayerInset = LocalMiniPlayerBottomInset.current

    Scaffold(
        modifier = scrollBehavior?.let { modifier.nestedScroll(it.nestedScrollConnection) } ?: modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (LocalSettingsPageHasTopBar.current) {
                Row(content = actions)
            } else if (compactTopBar) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = actions,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = title,
                            fontWeight = MaterialTheme.typography.titleLargeEmphasized.fontWeight,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    actions = actions,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = miniPlayerInset)) {
                floatingActionButton()
            }
        },
    ) { paddingValues ->
        // on Mochy's header the top padding sits inside the scroll, so content scrolls up under the header
        val headerTopPadding = if (LocalSettingsPageHasTopBar.current) LocalSettingsPageTopPadding.current else 0.dp
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            // the pinned header only takes the header's room when it has something in it, or Mochy's page opens on a gap
            Layout(content = persistentHeader) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
                val height = placeables.maxOfOrNull { it.height } ?: 0
                val top = if (height > 0) headerTopPadding.roundToPx() else 0
                layout(constraints.maxWidth, if (height > 0) height + top else 0) { placeables.forEach { it.place(0, top) } }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (contentScrollable) {
                                Modifier.verticalScroll(rememberScrollState()).padding(bottom = miniPlayerInset)
                            } else {
                                Modifier.padding(bottom = miniPlayerInset)
                            },
                        ).focusGroup()
                        .padding(vertical = 16.dp)
                        .padding(top = headerTopPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSubScreenGroup(
    title: String? = null,
    description: String? = null,
    isBeta: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    // the dark theme titles each group as Mochy does: bold white, just inside its card's edge
    val mochy = !LocalIsTv.current && MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier.fillMaxWidth().focusGroup(),
    ) {
        title?.let {
            Text(
                text = if (isBeta) "$it ${stringResource(Res.string.beta_tag)}" else it,
                style =
                    if (mochy) {
                        MaterialTheme.typography.titleSmall.copy(
                            fontFamily = mochyInterFontFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    } else {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                    },
                color = if (mochy) Color.White else titleColor,
                modifier =
                    Modifier.padding(
                        bottom = if (description == null) 12.dp else 4.dp,
                        start = 16.dp,
                        end = 32.dp,
                    ),
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        bottom = 12.dp,
                        start = 16.dp,
                        end = 24.dp,
                    ),
            )
        }

        content()
    }
}

@Composable
fun SettingsSubScreenGroup(
    title: String? = null,
    description: String? = null,
    isBeta: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    items: List<SettingsSubScreenItem>,
) {
    SettingsSubScreenGroup(
        title = title,
        description = description,
        isBeta = isBeta,
        titleColor = titleColor,
    ) {
        if (items.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEachIndexed { index, item ->
                    SettingsSubScreenItemRow(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == items.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SettingsSubScreenItemRow(
    item: SettingsSubScreenItem,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val isTv = LocalIsTv.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // the dark theme draws Mochy's grouped rows: one dark card with 28dp ends, the icon in a light circle
    val mochy = !isTv && MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape =
        if (mochy) {
            RoundedCornerShape(
                topStart = if (isFirst) 28.dp else 0.dp,
                topEnd = if (isFirst) 28.dp else 0.dp,
                bottomStart = if (isLast) 28.dp else 0.dp,
                bottomEnd = if (isLast) 28.dp else 0.dp,
            )
        } else {
            segmentedGroupShape(isFirst, isLast, isPressed)
        }
    val backgroundColor by animateColorAsState(
        targetValue =
            if (mochy) {
                if (isPressed) MochyRowPressed else MochyRowColor
            } else if (isPressed) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        label = "backgroundColor",
    )

    Box(item.modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = item.enabled && item.onClick != null }
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(
                        color = backgroundColor,
                        shape = shape,
                    ).tvFocusHighlight(shape)
                    .then(
                        if (item.checked != null) {
                            Modifier.toggleable(
                                value = item.checked,
                                enabled = item.enabled && item.onClick != null,
                                role = Role.Switch,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onValueChange = { item.onClick?.invoke() },
                            )
                        } else {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                enabled = item.enabled && item.onClick != null,
                                onClick = { item.onClick?.invoke() },
                            )
                        },
                    ).padding(
                        horizontal = if (mochy) 20.dp else 16.dp,
                        vertical = if (mochy) 14.dp else 16.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.icon?.let { icon ->
                Box(
                    modifier = if (mochy) Modifier.size(32.dp).clip(CircleShape).background(MochyIconCircle) else Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (mochy) MochyIconInk else item.iconTint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                        modifier = Modifier.size(if (mochy) 16.dp else 24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(if (mochy) 16.dp else 12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (item.isBeta) "${item.title} ${stringResource(Res.string.beta_tag)}" else item.title,
                    style =
                        if (mochy) {
                            MaterialTheme.typography.titleSmall.copy(
                                fontFamily = mochyInterFontFamily(),
                                color =
                                    if (item.enabled) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.W500,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.15.sp,
                                color =
                                    if (!item.enabled) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                    maxLines = if (item.wrapTitle) Int.MAX_VALUE else 1,
                    modifier = if (item.wrapTitle) Modifier else Modifier.singleLineMarquee(),
                )

                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = desc,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.W400,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.4.sp,
                                color =
                                    if (!item.enabled) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            ),
                    )
                }
            }

            item.trailingContent?.let { trailing ->
                Spacer(modifier = Modifier.width(8.dp))
                CompositionLocalProvider(LocalSettingsRowIsTv provides isTv) { trailing() }
            }
        }
        item.menuContent?.invoke(this)
    }
}

internal val LocalSettingsRowIsTv = staticCompositionLocalOf { false }

// Mochy's grouped settings card, its icon circle and the icon on it
private val MochyRowColor = Color(0xFF1C1C1E)
private val MochyRowPressed = Color(0xFF26262A)
private val MochyIconCircle = Color(0xFFE5E5EA)
private val MochyIconInk = Color(0xFF1C1C1E)

data class SettingsSubScreenItem(
    val title: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified,
    val trailingContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val isBeta: Boolean = false,
    val checked: Boolean? = null,
    val onClick: (() -> Unit)? = null,
    val modifier: Modifier = Modifier,
    val menuContent: (@Composable BoxScope.() -> Unit)? = null,
    val wrapTitle: Boolean = false,
)
