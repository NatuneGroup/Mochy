// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's settings list: the mark over a big "Settings", a round search field, one bubble per page. Scrolling folds the
// title away and the search field glides up beside the profile picture.

package com.metrolist.shared.ui.settings

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import com.metrolist.shared.generated.resources.mochy_settings_cast_connected
import com.metrolist.shared.generated.resources.mochy_settings_equalizer
import com.metrolist.shared.generated.resources.mochy_settings_integration
import com.metrolist.shared.generated.resources.mochy_settings_library_add
import com.metrolist.shared.generated.resources.mochy_settings_lyrics
import com.metrolist.shared.generated.resources.mochy_settings_palette
import com.metrolist.shared.generated.resources.mochy_settings_play
import com.metrolist.shared.generated.resources.mochy_settings_settings
import com.metrolist.shared.generated.resources.mochy_settings_storage
import com.metrolist.shared.generated.resources.settings
import com.metrolist.shared.generated.resources.settings_search_no_results
import com.metrolist.shared.generated.resources.settings_search_placeholder
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.utils.supportsNativeBlur
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal object MochySettingsColors {
    val Page = Color.Black

    // the open page beside the desktop pane: a step up from the black so the two read apart
    val Pane = Color(0xFF0C0C0D)
    val Bubble = Color(0xFF1C1C1E)
    val BubbleSelected = Color(0xFF3A3A3E)
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
    // the page open beside the list on a wide window, lit like the desktop pane's bubble
    selectedRoute: String? = null,
) {
    val scrollState = rememberScrollState()
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val searching = searchFocused || query.isNotBlank()
    val results = rememberSettingsSearchResults(registry, query)
    val recommendations = remember(registry) { pickMochyRecommendations(registry) }
    val collapse by rememberMochyCollapse(scrollState)
    val contentAlpha by animateFloatAsState(
        targetValue = if (searching) 0f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "mochySettingsContent",
    )
    val showContent by remember { derivedStateOf { contentAlpha > 0.001f } }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusBar + TopClearance + TitleBlockHeight + FieldHeight + 32.dp

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
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = contentAlpha },
                ) {
                    MochyRecommendedCard(
                        recommendations = recommendations,
                        registry = registry,
                        onOpen = onSearchResultSelected,
                    )

                    Spacer(Modifier.height(18.dp))

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(MochySettingsColors.Divider),
                    )

                    Spacer(Modifier.height(18.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        mochyHomePages(registry, host).forEach { pageId ->
                            registry[pageId]?.let { page ->
                                MochyCategoryBubble(
                                    title = page.title,
                                    icon = { tint -> MochyPageIcon(pageId, page.icon, host, tint) },
                                    selected = pageId.name == selectedRoute,
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
    val blur = supportsNativeBlur()
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
                            // the reference blurs the folding title in 3px steps up to 12px, clamped at the edges
                            val blurPx = (((p * 14f) / 3f).toInt() * 3).toFloat()
                            renderEffect = if (blur && blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
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
            Icon(
                MetrolistIcons.Outlined.Person,
                contentDescription = stringResource(Res.string.back),
                tint = MochySettingsColors.Secondary,
                modifier = Modifier.size(18.dp),
            )
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
            Spacer(Modifier.width(10.dp))
            Icon(
                MetrolistIcons.Default.Close,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

// the reference's order for the bubbles it has; pages it doesn't name follow in the registry's order
private val MochyHomePageOrder =
    listOf(
        SettingsPageId.AUDIO_LAB,
        SettingsPageId.PLAYER,
        SettingsPageId.LYRICS,
        SettingsPageId.CONTENT,
        SettingsPageId.APPEARANCE,
        SettingsPageId.STORAGE,
        SettingsPageId.DISCORD,
        SettingsPageId.LAST_FM,
        SettingsPageId.LISTEN_BRAINZ,
        SettingsPageId.LISTEN_TOGETHER,
        SettingsPageId.DEVICE_CONNECT,
        SettingsPageId.MOCHY_MODULES,
    )

// the pages on Mochy's list: the registry's root groups, plus the reference's own bubbles that live outside them
internal fun mochyHomePages(
    registry: SettingsUiRegistry,
    host: SharedSettingsHost,
): List<SettingsPageId> {
    val rootPages = registry.rootGroups.flatMap { it.pages }.filter { registry[it] != null }
    val named = MochyHomePageOrder.filter { it in host.availablePages && registry[it] != null }
    return (named + rootPages).distinct().sortedBy { MochyHomePageOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
}

// the reference's drawable for each bubble it has one for; other pages keep the registry icon
private fun mochyPageDrawable(pageId: SettingsPageId): DrawableResource? =
    when (pageId) {
        SettingsPageId.AUDIO_LAB -> Res.drawable.mochy_settings_equalizer
        SettingsPageId.PLAYER -> Res.drawable.mochy_settings_play
        SettingsPageId.LYRICS -> Res.drawable.mochy_settings_lyrics
        SettingsPageId.CONTENT -> Res.drawable.mochy_settings_settings
        SettingsPageId.APPEARANCE -> Res.drawable.mochy_settings_palette
        SettingsPageId.STORAGE -> Res.drawable.mochy_settings_storage
        SettingsPageId.DISCORD, SettingsPageId.LAST_FM, SettingsPageId.LISTEN_BRAINZ, SettingsPageId.LISTEN_TOGETHER ->
            Res.drawable.mochy_settings_integration
        SettingsPageId.DEVICE_CONNECT -> Res.drawable.mochy_settings_cast_connected
        SettingsPageId.MOCHY_MODULES -> Res.drawable.mochy_settings_library_add
        else -> null
    }

@Composable
private fun MochyPageIcon(
    pageId: SettingsPageId,
    fallback: ImageVector,
    host: SharedSettingsHost,
    tint: Color,
) {
    val custom = host.customPageIcons[pageId]
    val drawable = mochyPageDrawable(pageId)
    when {
        custom != null -> custom(tint)
        drawable != null ->
            Icon(
                painter = painterResource(drawable),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        else -> Icon(fallback, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MochyCategoryBubble(
    title: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    // the desktop pane marks the open page by lifting its bubble a shade; the reference switches it at once
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) MochySettingsColors.BubbleSelected else MochySettingsColors.Bubble)
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
                        // the reference uses the gear for every result, whatever page it lands on
                        Icon(
                            MetrolistIcons.Outlined.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            text = result.item?.title ?: result.page.title,
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Text(
                            text = result.page.title,
                            fontFamily = font,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // the same flat list, order and icons as the phone home
                items(mochyHomePages(registry, host), key = { it.name }) { pageId ->
                    registry[pageId]?.let { page ->
                        MochyCategoryBubble(
                            title = page.title,
                            icon = { tint -> MochyPageIcon(pageId, page.icon, host, tint) },
                            selected = pageId == selectedPageId,
                            onClick = { onPageSelected(pageId) },
                        )
                    }
                }
            }
        }
    }
}
