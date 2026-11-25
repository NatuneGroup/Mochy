// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's settings list: the mark over a big "Settings", a round search field, one bubble per page. Scrolling folds the
// title away and the search field glides up beside the profile picture.

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_name
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.mochy_mark
import com.metrolist.shared.generated.resources.settings
import com.metrolist.shared.generated.resources.settings_search_no_results
import com.metrolist.shared.generated.resources.settings_search_placeholder
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object MochySettingsColors {
    val Page = Color.Black

    // the open page beside the desktop pane: a step up from the black so the two read apart
    val Pane = Color(0xFF0C0C0D)
    val Bubble = Color(0xFF1C1C1E)
    val BubbleSelected = Color(0xFF2E2E31)
    val IconBackground = Color(0xFF333338)
    val Field = Color(0xFF141416)
    val FieldFocused = Color(0xFF1E1E21)
    val Avatar = Color(0xFF1C1C1F)
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)
    val Divider = Color.White.copy(alpha = 0.1f)
}

private val TitleBlockHeight = 88.dp
private val MarkSize = 32.dp
private val FieldHeight = 48.dp
private val AvatarSize = 36.dp
private val TopClearance = 18.dp
private val SidePadding = 24.dp
private val CollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
private const val COLLAPSE_MILLIS = 900

@Composable
internal fun MochySettingsHome(
    registry: SettingsUiRegistry,
    host: SharedSettingsHost,
    onPageSelected: (SettingsPageId) -> Unit,
    onSearchResultSelected: (SettingsPageId, String?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val searching = searchFocused || query.isNotBlank()
    val results = rememberSettingsSearchResults(registry, query)
    val collapse by rememberMochyCollapse(scrollState)
    val contentAlpha by animateFloatAsState(
        targetValue = if (searching) 0f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "mochySettingsContent",
    )
    val showContent by remember { derivedStateOf { contentAlpha > 0.001f } }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusBar + TopClearance + TitleBlockHeight + FieldHeight + 32.dp
    val font = mochyInterFontFamily()

    Box(Modifier.fillMaxSize().background(MochySettingsColors.Page)) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Spacer(Modifier.height(headerHeight))

            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter =
                    fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 20 },
                exit = fadeOut(tween(120, easing = FastOutSlowInEasing)),
            ) {
                MochySearchResults(results, onSearchResultSelected, Modifier.padding(horizontal = 20.dp))
            }

            if (showContent) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).graphicsLayer { alpha = contentAlpha },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    registry.rootGroups.filter { it.pages.isNotEmpty() }.forEachIndexed { groupIndex, group ->
                        // Mochy has no group headings, just a hairline between kinds of pages
                        if (groupIndex > 0) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(0.8.dp).background(MochySettingsColors.Divider))
                        }
                        group.pages.forEach { pageId ->
                            registry[pageId]?.let { page ->
                                MochyCategoryBubble(
                                    title = page.title,
                                    icon = { tint ->
                                        val custom = host.customPageIcons[pageId]
                                        if (custom != null) {
                                            custom(tint)
                                        } else {
                                            Icon(page.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                    onClick = { onPageSelected(pageId) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(maxOf(340.dp, host.bottomContentPadding + 160.dp)))
        }

        MochySettingsTopBar(
            collapse = { collapse },
            statusBar = statusBar,
            onAvatarClick = host.onClose,
            field = {
                MochySearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.settings_search_placeholder),
                    focused = searchFocused,
                    onFocusChanged = { searchFocused = it },
                )
            },
        )
    }
}

@Composable
private fun MochySettingsTopBar(
    collapse: () -> Float,
    statusBar: Dp,
    onAvatarClick: () -> Unit,
    field: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val travel = with(density) { TitleBlockHeight.toPx() }
    val page = MochySettingsColors.Page
    Box(Modifier.fillMaxWidth()) {
        // the page colour builds up under the bar once the title has folded away
        Box(
            Modifier.matchParentSize().drawWithCache {
                onDrawBehind {
                    val p = collapse().coerceIn(0f, 1f)
                    if (p <= 0.01f) return@onDrawBehind
                    drawRect(
                        Brush.verticalGradient(
                            0f to page.copy(alpha = p * 0.95f),
                            0.45f to page.copy(alpha = p * 0.72f),
                            0.78f to page.copy(alpha = p * 0.34f),
                            1f to Color.Transparent,
                        ),
                    )
                }
            },
        )
        Column(Modifier.padding(top = statusBar + TopClearance)) {
            Row(
                modifier =
                    Modifier
                        .height(TitleBlockHeight)
                        .fillMaxWidth()
                        .padding(horizontal = SidePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f).graphicsLayer {
                            val p = collapse().coerceIn(0f, 1f)
                            alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                            translationY = -p * travel
                            val scale = 1f - p * 0.18f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.mochy_mark),
                        contentDescription = stringResource(Res.string.app_name),
                        tint = Color.White,
                        modifier = Modifier.size(MarkSize),
                    )
                    Text(
                        text = stringResource(Res.string.settings),
                        fontFamily = mochyInterFontFamily(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                // beside the mark at rest, it drops to the search field's middle as the header folds
                MochySettingsAvatar(
                    onClick = onAvatarClick,
                    modifier =
                        Modifier.graphicsLayer {
                            val lift = (TitleBlockHeight / 2 - MarkSize / 2).toPx()
                            val drop = (TitleBlockHeight / 2 + FieldHeight / 2).toPx()
                            val p = collapse().coerceIn(0f, 1f)
                            translationY = -(1f - p) * lift + p * (drop - travel)
                        },
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = SidePadding)
                        .graphicsLayer { translationY = -collapse().coerceIn(0f, 1f) * travel },
            ) {
                // the field keeps the avatar's room once it rises beside it
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = SidePadding)
                        .layout { measurable, constraints ->
                            val reserve = ((AvatarSize + 12.dp).toPx() * collapse().coerceIn(0f, 1f)).roundToInt()
                            val width = (constraints.maxWidth - reserve).coerceAtLeast(0)
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                        },
                ) {
                    field()
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MochySettingsAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = Settings.shared
    val url = settings.mochyProfilePhotoUri.ifBlank { settings.accountThumbnail }.trim()
    val model =
        when {
            url.isEmpty() -> null
            url.contains("://") -> url
            else -> "file://$url"
        }
    Box(
        modifier =
            modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MochySettingsColors.Avatar)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(Res.string.back),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(MetrolistIcons.Outlined.Person, contentDescription = stringResource(Res.string.back), tint = MochySettingsColors.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MochySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .clip(RoundedCornerShape(50))
                .background(if (focused) MochySettingsColors.FieldFocused else MochySettingsColors.Field)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MetrolistIcons.Outlined.Search, contentDescription = null, tint = MochySettingsColors.Placeholder, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = MochySettingsColors.Placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = mochyInterFontFamily()),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                MetrolistIcons.Default.Close,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun MochyCategoryBubble(
    title: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    // the desktop pane marks the open page by lifting its bubble a shade
    val background by animateColorAsState(
        if (selected) MochySettingsColors.BubbleSelected else MochySettingsColors.Bubble,
        tween(180, easing = FastOutSlowInEasing),
        label = "mochyBubble",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MochySettingsColors.IconBackground),
            contentAlignment = Alignment.Center,
        ) {
            icon(Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontFamily = mochyInterFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Mochy's latch-and-settle fold: past the threshold the header always finishes folding, even once the finger lifts
@Composable
private fun rememberMochyCollapse(scrollState: ScrollState): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val latch = remember(scrollState) { booleanArrayOf(false) }
    val collapsed by remember(scrollState, enterPx, exitPx) {
        derivedStateOf {
            val offset = scrollState.value.toFloat()
            val next =
                when {
                    offset > enterPx -> true
                    offset < exitPx -> false
                    else -> latch[0]
                }
            latch[0] = next
            next
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(COLLAPSE_MILLIS, easing = CollapseEasing),
        label = "mochySettingsCollapse",
    )
}

@Composable
private fun MochySearchResults(
    results: List<SettingsSearchResult>,
    onSearchResultSelected: (SettingsPageId, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val font = mochyInterFontFamily()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (results.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_search_no_results),
                fontFamily = font,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
        } else {
            results.forEach { result ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MochySettingsColors.Bubble)
                            .clickable {
                                focusManager.clearFocus()
                                onSearchResultSelected(result.page.id, result.item?.id)
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(MochySettingsColors.IconBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(result.page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            text = result.item?.title ?: result.page.title,
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = result.page.title,
                            fontFamily = font,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Settings on a desktop window, Mochy's way: the mark over a big title, the round search and one bubble per
 * page in a side pane, the open page lifted a shade. The page itself fills the space beside it.
 */
@Composable
internal fun MochySettingsNavigationPane(
    registry: SettingsUiRegistry,
    host: SharedSettingsHost,
    selectedPageId: SettingsPageId?,
    onPageSelected: (SettingsPageId) -> Unit,
    onSearchResultSelected: (SettingsPageId, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val results = rememberSettingsSearchResults(registry, query)
    val listState = rememberLazyListState()
    Column(modifier.background(MochySettingsColors.Page).padding(top = TopClearance)) {
        Column(
            modifier = Modifier.padding(horizontal = SidePadding).height(TitleBlockHeight),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(Res.drawable.mochy_mark),
                contentDescription = stringResource(Res.string.app_name),
                tint = Color.White,
                modifier = Modifier.size(MarkSize),
            )
            Text(
                text = stringResource(Res.string.settings),
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
        Box(Modifier.padding(horizontal = SidePadding)) {
            MochySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.settings_search_placeholder),
                focused = searchFocused,
                onFocusChanged = { searchFocused = it },
            )
        }
        Spacer(Modifier.height(20.dp))
        if (query.isNotBlank()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MochySearchResults(results, onSearchResultSelected, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(24.dp + host.bottomContentPadding))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + host.bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                registry.rootGroups.filter { it.pages.isNotEmpty() }.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        item("divider:" + group.title) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(0.8.dp).background(MochySettingsColors.Divider))
                        }
                    }
                    items(group.pages.size, key = { group.pages[it].name }) { index ->
                        val pageId = group.pages[index]
                        registry[pageId]?.let { page ->
                            MochyCategoryBubble(
                                title = page.title,
                                selected = pageId == selectedPageId,
                                icon = { tint ->
                                    val custom = host.customPageIcons[pageId]
                                    if (custom != null) {
                                        custom(tint)
                                    } else {
                                        Icon(page.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                    }
                                },
                                onClick = { onPageSelected(pageId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
