/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.remove_recent_search_action
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.SearchFilter
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.ui.tv.tvFocusHighlight
import com.metrolist.shared.ui.utils.supportsNativeBlur
import com.metrolist.shared.viewmodels.HomeViewModel
import com.metrolist.shared.viewmodels.SearchViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Mochy's search palette: the pill, the suggestion tiles and the section rules. */
internal object MochySearchColors {
    val Field = Color(0xFF141416)
    val FieldFocused = Color(0xFF1E1E21)
    val Tile = Color(0xFF121214)
    val Divider = Color(0xFF232326)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)
    val TileHigh = Color(0xFF1C1C1F)
}

data class SearchScreenText(
    val contentText: SearchContentText,
    val searchPlaceholder: String,
    val back: String,
    val clear: String,
    val microphoneContentDescription: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchViewModel.SearchUiState,
    text: SearchScreenText,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    onVideoItemClick: (YTItem) -> Unit = onItemClick,
    onItemLongClick: (YTItem) -> Unit,
    onSongSave: (SongItem) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemInsert: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFilterSelected: (SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRemoveHistoryItem: (String) -> Unit = {},
    onRemoveRecentItem: (YTItem) -> Unit = {},
    onClearRecentItems: () -> Unit = {},
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    onShuffleClick: (YTItem) -> Unit = {},
    onMixClick: (YTItem) -> Unit = {},
    itemContainer: @Composable (YTItem, @Composable () -> Unit) -> Unit = { _, content -> content() },
    onMicrophoneClick: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    showSearchBar: Boolean = true,
    searchBarEndPadding: Dp = 0.dp,
    onAlbumPlay: ((AlbumItem) -> Unit)? = null,
    moodsAndGenres: List<HomeViewModel.Genre> = emptyList(),
    onAvatarClick: (() -> Unit)? = null,
) {
    val query = uiState.query
    val isQuerySubmitted = uiState.isQuerySubmitted
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWide = maxWidth > 600.dp

            val listState = rememberLazyListState()
            val collapseState = rememberSearchCollapse(listState)
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val travelPx = with(LocalDensity.current) { HeaderTitleHeight.toPx() }
            val headerLayout = if (showSearchBar) statusBar + HeaderClearance + HeaderTitleHeight + HeaderFieldRow + HeaderGap else 0.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(headerLayout))
                    SearchContent(
                        uiState = uiState,
                        text = text.contentText,
                        onItemClick = {
                            dismissKeyboard()
                            onItemClick(it)
                        },
                        onVideoItemClick = {
                            dismissKeyboard()
                            onVideoItemClick(it)
                        },
                        onItemLongClick = onItemLongClick,
                        onSongSave = onSongSave,
                        onHistoryItemClick = {
                            dismissKeyboard()
                            onHistoryItemClick(it)
                        },
                        onHistoryItemInsert = onHistoryItemInsert,
                        onClearHistory = onClearHistory,
                        onFilterSelected = onFilterSelected,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                        onRemoveHistoryItem = onRemoveHistoryItem,
                        onRemoveRecentItem = onRemoveRecentItem,
                        onClearRecentItems = onClearRecentItems,
                        currentSongId = currentSongId,
                        isPlaying = isPlaying,
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onShuffleClick = onShuffleClick,
                        onMixClick = onMixClick,
                        itemContainer = itemContainer,
                        contentPadding = contentPadding,
                        listState = listState,
                        moodsAndGenres = moodsAndGenres,
                        modifier =
                            Modifier.weight(1f).graphicsLayer {
                                // the page rises under the folding header
                                translationY = if (showSearchBar) -collapseState.value * travelPx else 0f
                            },
                        onAlbumPlay = onAlbumPlay,
                    )
                }

                if (showSearchBar) {
                    SearchHeader(
                        collapse = { collapseState.value },
                        statusBar = statusBar,
                        onAvatarClick = onAvatarClick,
                        field = {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(end = searchBarEndPadding),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(modifier = if (isWide) Modifier.widthIn(max = 1400.dp).fillMaxWidth() else Modifier.fillMaxWidth()) {
                                    SearchBar(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        onClear = { onQueryChange("") },
                                        onBack = onBack,
                                        onMicrophoneClick = onMicrophoneClick,
                                        microphoneContentDescription = text.microphoneContentDescription,
                                        isQuerySubmitted = isQuerySubmitted,
                                        focusRequester = focusRequester,
                                        placeholder = text.searchPlaceholder,
                                        backLabel = text.back,
                                        clearLabel = text.clear,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private val HeaderClearance = 18.dp
private val HeaderTitleHeight = 62.dp
private val HeaderFieldRow = 52.dp
private val HeaderGap = 16.dp
private val HeaderAvatarSize = 36.dp

/** the title folds up and the field takes its place as the list scrolls, the same as the Mochy settings header */
@Composable
private fun rememberSearchCollapse(listState: LazyListState): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val latch = remember(listState) { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx, exitPx) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val next =
                when {
                    listState.firstVisibleItemIndex > 0 -> true
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
        animationSpec = tween(durationMillis = 900, easing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)),
        label = "searchCollapse",
    )
}

@Composable
private fun SearchHeader(
    collapse: () -> Float,
    statusBar: Dp,
    onAvatarClick: (() -> Unit)?,
    field: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val travel = with(density) { HeaderTitleHeight.toPx() }
    val avatarDrop = with(density) { (HeaderFieldRow / 2 - HeaderTitleHeight / 2).toPx() }
    val avatarReserve = with(density) { (HeaderAvatarSize + 12.dp).toPx() }
    val blur = supportsNativeBlur()
    Box(modifier = Modifier.fillMaxWidth()) {
        // the page colour builds up under the bar once the title has folded away
        Box(
            modifier =
                Modifier.matchParentSize().drawWithCache {
                    onDrawBehind {
                        val p = collapse().coerceIn(0f, 1f)
                        if (p <= 0.01f) return@onDrawBehind
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = p * 0.95f),
                                0.45f to Color.Black.copy(alpha = p * 0.72f),
                                0.78f to Color.Black.copy(alpha = p * 0.34f),
                                1f to Color.Transparent,
                            ),
                        )
                    }
                },
        )
        Column(modifier = Modifier.padding(top = statusBar + HeaderClearance)) {
            Row(
                modifier =
                    Modifier
                        .height(HeaderTitleHeight)
                        .fillMaxWidth()
                        .padding(horizontal = SearchHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.search),
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    color = MochySearchColors.Primary,
                    maxLines = 1,
                    modifier =
                        Modifier.weight(1f).graphicsLayer {
                            val p = collapse().coerceIn(0f, 1f)
                            alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                            translationY = -p * travel
                            val scale = 1f - p * 0.18f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            // the title blurs in 3px steps as it folds, clamped at the edges, where the platform can blur
                            val blurPx = (((p * 14f) / 3f).toInt() * 3).toFloat()
                            renderEffect = if (blur && blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
                        },
                )
                if (onAvatarClick != null) {
                    SearchHeaderAvatar(
                        onClick = onAvatarClick,
                        modifier =
                            Modifier.graphicsLayer {
                                // beside the title at rest, it drops to the field's middle as the header folds
                                translationY = collapse().coerceIn(0f, 1f) * avatarDrop
                            },
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = -collapse().coerceIn(0f, 1f) * travel }
                        .layout { measurable, constraints ->
                            // the field keeps the avatar's room once it rises beside it
                            val reserve = if (onAvatarClick == null) 0 else (avatarReserve * collapse().coerceIn(0f, 1f)).roundToInt()
                            val width = (constraints.maxWidth - reserve).coerceAtLeast(0)
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                        },
            ) {
                field()
            }
        }
    }
}

@Composable
private fun SearchHeaderAvatar(
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
                .size(HeaderAvatarSize)
                .clip(CircleShape)
                .background(MochySearchColors.TileHigh)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(url = model, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                imageVector = MetrolistIcons.Outlined.Person,
                contentDescription = null,
                tint = MochySearchColors.Secondary,
                modifier = Modifier.size(HeaderAvatarSize * 0.5f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onMicrophoneClick: (() -> Unit)?,
    microphoneContentDescription: String,
    isQuerySubmitted: Boolean,
    focusRequester: FocusRequester,
    placeholder: String,
    backLabel: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    history: List<String> = emptyList(),
    onHistoryItemClick: (String) -> Unit = {},
    onRemoveHistoryItem: (String) -> Unit = {},
) {
    val isTv = LocalIsTv.current
    val textFieldState = rememberTextFieldState(initialText = query)
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val currentQuery by rememberUpdatedState(query)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    var tvKeyboardRequested by remember { mutableStateOf(false) }
    val submitSearch: (String) -> Unit = {
        onSearch(it)
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(query) {
        if (textFieldState.text.toString() != query) {
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { text ->
            if (text != currentQuery) onQueryChange(text)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .padding(horizontal = 24.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        run {
            val barHeight = 48.dp
            val iconSize = 20.dp
            val fontSize = 15.sp

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .background(if (isFocused) MochySearchColors.FieldFocused else MochySearchColors.Field, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isQuerySubmitted) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(barHeight).tvFocusHighlight(CircleShape, scale = 1f),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = MetrolistIcons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backLabel,
                                modifier = Modifier.size(iconSize),
                                tint = MochySearchColors.Secondary,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = MetrolistIcons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = MochySearchColors.Secondary,
                        )
                    }

                    BasicTextField(
                        state = textFieldState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(barHeight)
                                .focusRequester(focusRequester)
                                .tvFocusHighlight(scale = 1f)
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (!it.isFocused) tvKeyboardRequested = false
                                }.onInterceptKeyBeforeSoftKeyboard { event ->
                                    if (isTv && tvKeyboardRequested && event.key == Key.Back) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            tvKeyboardRequested = false
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }.onPreviewKeyEvent { event ->
                                    if (isTv && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            tvKeyboardRequested = true
                                            keyboardController?.show()
                                        }
                                        true
                                    } else if (isTv) {
                                        val direction =
                                            when (event.key) {
                                                Key.DirectionLeft -> FocusDirection.Left
                                                Key.DirectionRight -> FocusDirection.Right
                                                Key.DirectionUp -> FocusDirection.Up
                                                Key.DirectionDown -> FocusDirection.Down
                                                else -> null
                                            }
                                        if (direction != null) {
                                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                },
                        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = !isTv || tvKeyboardRequested),
                        textStyle =
                            TextStyle(
                                fontFamily = mochyInterFontFamily(),
                                color = MochySearchColors.Primary,
                                fontSize = fontSize,
                            ),
                        cursorBrush = SolidColor(MochySearchColors.Primary),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        onKeyboardAction = { submitSearch(textFieldState.text.toString()) },
                        decorator = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(fontFamily = mochyInterFontFamily(), fontSize = fontSize),
                                        color = MochySearchColors.Placeholder,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(barHeight).tvFocusHighlight(CircleShape, scale = 1f),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = MetrolistIcons.Default.Clear,
                                contentDescription = clearLabel,
                                modifier = Modifier.size(iconSize),
                                tint = MochySearchColors.Secondary,
                            )
                        }
                    } else if (onMicrophoneClick != null) {
                        IconButton(
                            onClick = onMicrophoneClick,
                            modifier = Modifier.size(barHeight).tvFocusHighlight(CircleShape, scale = 1f),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = MetrolistIcons.Rounded.Mic,
                                contentDescription = microphoneContentDescription,
                                modifier = Modifier.size(iconSize),
                                tint = MochySearchColors.Secondary,
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = !isTv && isFocused && history.isNotEmpty() && query.isEmpty(),
            onDismissRequest = { isFocused = false },
            offset = DpOffset(0.dp, 4.dp),
            modifier =
                Modifier
                    .width(maxWidth)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                history.forEach { historyItem ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onHistoryItemClick(historyItem)
                                    isFocused = false
                                }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            MetrolistIcons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = historyItem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onRemoveHistoryItem(historyItem) },
                            modifier = Modifier.size(48.dp),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                MetrolistIcons.Default.Close,
                                contentDescription = stringResource(Res.string.remove_recent_search_action),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
