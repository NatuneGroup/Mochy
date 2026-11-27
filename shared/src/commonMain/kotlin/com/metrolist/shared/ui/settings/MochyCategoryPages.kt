// Ported from Mochy Music by realidkroo (github.com/realidkroo/mochy-music). Licensed under GPL-3.0.

// Mochy's settings categories: Playback, Now playing, App behaviour, Customisation, Integrations and Devices,
// each one page of titled sections on Mochy's grouped cards with the page's own search on top. The rows are the
// app's own settings, taken from the settings registry and placed where Mochy keeps them, so every setting stays
// wired the same way it is on the plain pages.

package com.metrolist.shared.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.appearance_auto_playlists
import com.metrolist.shared.generated.resources.appearance_misc
import com.metrolist.shared.generated.resources.appearance_navigation_tabs
import com.metrolist.shared.generated.resources.appearance_player
import com.metrolist.shared.generated.resources.appearance_theme
import com.metrolist.shared.generated.resources.content_filters_filtering
import com.metrolist.shared.generated.resources.display_density
import com.metrolist.shared.generated.resources.downloads
import com.metrolist.shared.generated.resources.general_app_settings
import com.metrolist.shared.generated.resources.history
import com.metrolist.shared.generated.resources.layout
import com.metrolist.shared.generated.resources.library
import com.metrolist.shared.generated.resources.lyrics_display
import com.metrolist.shared.generated.resources.mochy_set_album
import com.metrolist.shared.generated.resources.mochy_set_bluetooth
import com.metrolist.shared.generated.resources.mochy_set_bug_report
import com.metrolist.shared.generated.resources.mochy_set_cached
import com.metrolist.shared.generated.resources.mochy_set_cast
import com.metrolist.shared.generated.resources.mochy_set_cast_connected
import com.metrolist.shared.generated.resources.mochy_set_delete
import com.metrolist.shared.generated.resources.mochy_set_download
import com.metrolist.shared.generated.resources.mochy_set_explicit
import com.metrolist.shared.generated.resources.mochy_set_fast_forward
import com.metrolist.shared.generated.resources.mochy_set_graphic_eq
import com.metrolist.shared.generated.resources.mochy_set_history
import com.metrolist.shared.generated.resources.mochy_set_image
import com.metrolist.shared.generated.resources.mochy_set_info
import com.metrolist.shared.generated.resources.mochy_set_language
import com.metrolist.shared.generated.resources.mochy_set_library_music
import com.metrolist.shared.generated.resources.mochy_set_linear_scale
import com.metrolist.shared.generated.resources.mochy_set_link
import com.metrolist.shared.generated.resources.mochy_set_lock
import com.metrolist.shared.generated.resources.mochy_set_lyrics
import com.metrolist.shared.generated.resources.mochy_set_music_note
import com.metrolist.shared.generated.resources.mochy_set_palette
import com.metrolist.shared.generated.resources.mochy_set_person
import com.metrolist.shared.generated.resources.mochy_set_play
import com.metrolist.shared.generated.resources.mochy_set_queue_music
import com.metrolist.shared.generated.resources.mochy_set_repeat
import com.metrolist.shared.generated.resources.mochy_set_settings
import com.metrolist.shared.generated.resources.mochy_set_share
import com.metrolist.shared.generated.resources.mochy_set_shuffle
import com.metrolist.shared.generated.resources.mochy_set_skip_next
import com.metrolist.shared.generated.resources.mochy_set_slow_motion_video
import com.metrolist.shared.generated.resources.mochy_set_smartphone
import com.metrolist.shared.generated.resources.mochy_set_sparks
import com.metrolist.shared.generated.resources.mochy_set_speed
import com.metrolist.shared.generated.resources.mochy_set_swipe
import com.metrolist.shared.generated.resources.mochy_set_sync
import com.metrolist.shared.generated.resources.mochy_set_timer
import com.metrolist.shared.generated.resources.mochy_set_translate
import com.metrolist.shared.generated.resources.mochy_set_tune
import com.metrolist.shared.generated.resources.mochy_set_update
import com.metrolist.shared.generated.resources.mochy_set_volume_off
import com.metrolist.shared.generated.resources.mochy_set_volume_up
import com.metrolist.shared.generated.resources.more
import com.metrolist.shared.generated.resources.privacy_blocklists
import com.metrolist.shared.generated.resources.privacy_diagnostics
import com.metrolist.shared.generated.resources.queue
import com.metrolist.shared.generated.resources.settings_artist_sources
import com.metrolist.shared.generated.resources.settings_cast_title
import com.metrolist.shared.generated.resources.settings_home_sections
import com.metrolist.shared.generated.resources.settings_layouts
import com.metrolist.shared.generated.resources.settings_localization
import com.metrolist.shared.generated.resources.settings_on_the_player
import com.metrolist.shared.generated.resources.settings_related
import com.metrolist.shared.generated.resources.settings_section_canvas
import com.metrolist.shared.generated.resources.settings_section_failures
import com.metrolist.shared.generated.resources.settings_section_library_privacy
import com.metrolist.shared.generated.resources.settings_section_listening
import com.metrolist.shared.generated.resources.settings_section_lyrics
import com.metrolist.shared.generated.resources.settings_section_videos
import com.metrolist.shared.generated.resources.settings_sound
import com.metrolist.shared.generated.resources.settings_transition_link_desc
import com.metrolist.shared.generated.resources.song_gestures
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The registry the category pages take their rows from, and what a page calls once a value has changed. */
internal class MochySettingsPageContext(
    val registry: SettingsUiRegistry,
    val onSettingValueChanged: () -> Unit,
)

internal val LocalMochySettingsPageContext = staticCompositionLocalOf<MochySettingsPageContext?> { null }

/** The categories Mochy's settings list shows, each a page of its own. */
internal val MochyCategoryPageIds =
    setOf(
        SettingsPageId.MOCHY_PLAYBACK,
        SettingsPageId.MOCHY_NOW_PLAYING,
        SettingsPageId.MOCHY_APP_BEHAVIOUR,
        SettingsPageId.MOCHY_CUSTOMISATION,
        SettingsPageId.MOCHY_INTEGRATIONS,
        SettingsPageId.MOCHY_DEVICES,
    )

/** The Mochy category page that holds a plain page's settings, for the plain pages Mochy folds into its own. */
internal fun mochyCategoryFor(pageId: SettingsPageId): SettingsPageId? =
    when (pageId) {
        SettingsPageId.PLAYER, SettingsPageId.PLAYER_QUEUE -> SettingsPageId.MOCHY_PLAYBACK
        SettingsPageId.LYRICS, SettingsPageId.PLAYER_ARTWORK -> SettingsPageId.MOCHY_NOW_PLAYING
        SettingsPageId.CONTENT, SettingsPageId.PRIVACY -> SettingsPageId.MOCHY_APP_BEHAVIOUR
        SettingsPageId.APPEARANCE -> SettingsPageId.MOCHY_CUSTOMISATION
        else -> null
    }

private sealed interface MochyEntry

// one registry row; subOf names the switch on the same page it sits under, shown while that switch is on
private class MochyRow(
    val page: SettingsPageId,
    val id: String,
    val icon: DrawableResource?,
    val subOf: String? = null,
) : MochyEntry

// every row of a registry group, in its order, all with one icon unless a row brings its own
private class MochyGroupRows(
    val page: SettingsPageId,
    val groupId: String,
    val icon: DrawableResource?,
    val except: Set<String> = emptySet(),
) : MochyEntry

// a row that opens another settings page, titled and described by that page
private class MochyPageLink(
    val page: SettingsPageId,
    val icon: DrawableResource?,
    val description: StringResource? = null,
) : MochyEntry

private class MochySection(
    val title: StringResource?,
    val entries: List<MochyEntry>,
)

// a page's sections, plus the registry pages it stands for: their rows no section names come last
private class MochyPageSpec(
    val sections: List<MochySection>,
    val covers: List<SettingsPageId> = emptyList(),
)

@Composable
internal fun MochyCategoryPage(
    pageId: SettingsPageId,
    onNavigate: (SettingsPageId) -> Unit,
) {
    if (pageId == SettingsPageId.MOCHY_INTEGRATIONS) {
        MochyIntegrationsPage(onNavigate)
        return
    }
    val context = LocalMochySettingsPageContext.current ?: return
    val spec = mochyPageSpec(pageId) ?: return
    val query = rememberSaveable(pageId) { mutableStateOf("") }
    val searchSlot = LocalMochySettingsSearchSlot.current
    val searchBar: @Composable () -> Unit =
        remember(query) { { MochySubSettingsSearchBar(query = query.value, onQueryChange = { query.value = it }) } }
    if (searchSlot != null) {
        DisposableEffect(searchSlot, searchBar) {
            searchSlot.value = searchBar
            onDispose { if (searchSlot.value === searchBar) searchSlot.value = null }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(LocalSettingsPageTopPadding.current))
        if (searchSlot != null) {
            Spacer(Modifier.height(MochySettingsSearchSpace))
        } else {
            searchBar()
            Spacer(Modifier.height(16.dp))
        }
        // Customisation opens on Mochy's cards: cover rounding and the player styles
        if (pageId == SettingsPageId.MOCHY_CUSTOMISATION && query.value.isBlank()) MochyCustomiseCards()
        val named = mutableSetOf<Pair<SettingsPageId, String>>()
        spec.sections.forEach { section ->
            val items = section.entries.flatMap { entry -> resolveEntry(entry, context, onNavigate, named) }
            MochySettingsSection(section.title?.let { stringResource(it) }, items.matching(query.value))
        }
        val leftover =
            spec.covers.flatMap { coveredPage ->
                context.registry[coveredPage]
                    ?.groups
                    .orEmpty()
                    .flatMap { it.items }
                    .filter { (coveredPage to it.id) !in named }
                    .map { coveredPage to it }
            }
        if (leftover.isNotEmpty()) {
            val items =
                leftover.map { (page, item) ->
                    key(page, item.id) { item.toMochyItem(page, leftoverIcon(page), null, context, onNavigate) }
                }
            MochySettingsSection(stringResource(Res.string.more), items.matching(query.value))
        }
        Spacer(Modifier.height(LocalSettingsPageBottomPadding.current + 140.dp))
    }
}

@Composable
private fun resolveEntry(
    entry: MochyEntry,
    context: MochySettingsPageContext,
    onNavigate: (SettingsPageId) -> Unit,
    named: MutableSet<Pair<SettingsPageId, String>>,
): List<MochySettingsItem> =
    when (entry) {
        is MochyRow -> {
            val items =
                context.registry[entry.page]
                    ?.groups
                    .orEmpty()
                    .flatMap { it.items }
            val item = items.firstOrNull { it.id == entry.id }
            if (item == null) {
                emptyList()
            } else {
                named += entry.page to entry.id
                val parentOn = entry.subOf?.let { parent -> items.firstOrNull { it.id == parent }?.checked == true }
                listOf(key(entry.page, entry.id) { item.toMochyItem(entry.page, entry.icon, parentOn, context, onNavigate) })
            }
        }

        is MochyGroupRows -> {
            context.registry[entry.page]
                ?.groups
                .orEmpty()
                .filter { it.id == entry.groupId }
                .flatMap { it.items }
                .filter { it.id !in entry.except }
                .map { item ->
                    named += entry.page to item.id
                    key(entry.page, item.id) { item.toMochyItem(entry.page, entry.icon, null, context, onNavigate) }
                }
        }

        is MochyPageLink -> {
            val page = context.registry[entry.page]
            if (page == null) {
                emptyList()
            } else {
                listOf(
                    mochyLinkItem(
                        key = entry.page.name,
                        title = page.title,
                        description = entry.description?.let { stringResource(it) } ?: page.subtitle,
                        icon = entry.icon?.let { painterResource(it) },
                        onClick = { onNavigate(entry.page) },
                    ),
                )
            }
        }
    }

// a registry row drawn as Mochy's: its own control on the right, a slider under the title, a sheet for a choice
@Composable
private fun SettingsItemRegistration.toMochyItem(
    page: SettingsPageId,
    fallbackIcon: DrawableResource?,
    parentOn: Boolean?,
    context: MochySettingsPageContext,
    onNavigate: (SettingsPageId) -> Unit,
): MochySettingsItem {
    val item = this
    val changed = context.onSettingValueChanged
    val icon = (item.icon ?: fallbackIcon)?.let { painterResource(it) }
    val highlightKey = "${page.name}:${item.id}"
    val visible = parentOn != false
    val isSub = parentOn != null
    val control = if (item.control == SettingsControl.CUSTOM && item.sliderRange != null) SettingsControl.SLIDER else item.control
    return when (control) {
        SettingsControl.SWITCH, SettingsControl.MAIN_SWITCH -> {
            mochySwitchItem(
                key = highlightKey,
                title = item.title,
                description = item.description,
                icon = icon,
                checked = item.checked == true,
                onCheckedChange = {
                    item.onCheckedChange?.invoke(it)
                    changed()
                },
                subOptionOf = parentOn,
                enabled = item.enabled,
            )
        }

        SettingsControl.CHOICE -> {
            var open by remember { mutableStateOf(false) }
            MochySettingsItem(
                icon = icon,
                title = { Text(item.title) },
                descriptionText = item.description,
                highlightKey = highlightKey,
                searchText = item.title,
                enabled = item.enabled,
                isVisible = visible,
                isSubOption = isSub,
                onClick = { open = true },
                trailingContent = {
                    MochyRowValue(item.valueLabel ?: item.choices.firstOrNull { it.id == item.selectedChoiceId }?.label)
                    MochyChoiceSheet(
                        title = item.title,
                        choices = item.choices,
                        selectedId = item.selectedChoiceId,
                        isVisible = open,
                        onSelect = { choiceId ->
                            item.onChoiceSelected?.invoke(choiceId)
                            changed()
                        },
                        onDismiss = { open = false },
                    )
                },
            )
        }

        SettingsControl.SLIDER -> {
            val range = item.sliderRange ?: 0f..1f
            var value by remember(item.sliderValue) { mutableFloatStateOf(item.sliderValue ?: range.start) }
            // the registry is rebuilt once the drag settles, not on every frame of it
            var dirty by remember { mutableStateOf(false) }
            LaunchedEffect(value, dirty) {
                if (!dirty) return@LaunchedEffect
                delay(SLIDER_SETTLE_MS)
                dirty = false
                changed()
            }
            val formatter = item.sliderValueFormatter
            MochySettingsItem(
                icon = icon,
                title = { MochyValueTitle(item.title, formatter?.invoke(value) ?: item.valueLabel.orEmpty()) },
                detail = item.description,
                highlightKey = highlightKey,
                searchText = item.title,
                enabled = item.enabled,
                isVisible = visible,
                isSubOption = isSub,
                description = {
                    MochySettingsSlider(
                        value = value,
                        onValueChange = {
                            value = it
                            item.onSliderChanged?.invoke(it)
                            dirty = true
                        },
                        valueRange = range,
                        steps = item.sliderSteps,
                        enabled = item.enabled,
                        label = item.title,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                },
            )
        }

        SettingsControl.TEXT, SettingsControl.ACTION -> {
            var dialog by remember { mutableStateOf(false) }
            MochySettingsItem(
                icon = icon,
                title = { Text(item.title) },
                descriptionText = item.description,
                highlightKey = highlightKey,
                searchText = item.title,
                enabled = item.enabled,
                isVisible = visible,
                isSubOption = isSub,
                onClick = {
                    if (item.control == SettingsControl.ACTION && item.confirmationMessage == null) {
                        item.onClick?.invoke()
                        changed()
                    } else {
                        dialog = true
                    }
                },
                trailingContent = {
                    MochyRowValue(if (item.secret) null else item.valueLabel)
                    if (dialog) {
                        if (item.control == SettingsControl.TEXT) {
                            SettingsTextDialog(item, onDismiss = { dialog = false }, onSettingValueChanged = changed)
                        } else {
                            SettingsConfirmationDialog(item, onDismiss = { dialog = false }, onSettingValueChanged = changed)
                        }
                    }
                },
            )
        }

        SettingsControl.NAVIGATION -> {
            mochyLinkItem(
                key = highlightKey,
                title = item.title,
                description = item.description,
                icon = icon,
                onClick = { item.destination?.let(onNavigate) ?: item.onClick?.invoke() },
                visible = visible,
                isSubOption = isSub,
                value = item.valueLabel,
            )
        }

        SettingsControl.CUSTOM -> {
            MochySettingsItem(
                icon = icon,
                title = { item.customContent?.invoke(changed) },
                highlightKey = highlightKey,
                searchText = item.title,
                descriptionText = null,
                detail = item.description,
                enabled = item.enabled,
                isVisible = visible,
                isSubOption = isSub,
            )
        }
    }
}

private const val SLIDER_SETTLE_MS = 300L

// every Mochy row has an icon; a row no section places takes its page's
private fun leftoverIcon(page: SettingsPageId): DrawableResource =
    when (page) {
        SettingsPageId.PLAYER_QUEUE -> Res.drawable.mochy_set_queue_music
        SettingsPageId.LYRICS -> Res.drawable.mochy_set_lyrics
        SettingsPageId.PLAYER_ARTWORK -> Res.drawable.mochy_set_image
        SettingsPageId.PRIVACY -> Res.drawable.mochy_set_lock
        SettingsPageId.APPEARANCE -> Res.drawable.mochy_set_palette
        else -> Res.drawable.mochy_set_settings
    }

@Composable
private fun MochyRowValue(value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (value != null) {
            Text(
                text = value,
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 128.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        MochyNextArrow()
    }
}

private fun mochyPageSpec(pageId: SettingsPageId): MochyPageSpec? =
    when (pageId) {
        SettingsPageId.MOCHY_PLAYBACK -> PlaybackSpec
        SettingsPageId.MOCHY_NOW_PLAYING -> NowPlayingSpec
        SettingsPageId.MOCHY_APP_BEHAVIOUR -> AppBehaviourSpec
        SettingsPageId.MOCHY_CUSTOMISATION -> CustomisationSpec
        SettingsPageId.MOCHY_DEVICES -> DevicesSpec
        else -> null
    }

private val P = SettingsPageId.PLAYER
private val L = SettingsPageId.LYRICS
private val A = SettingsPageId.APPEARANCE
private val C = SettingsPageId.CONTENT
private val V = SettingsPageId.PRIVACY

// settings > playback: how music keeps going, what happens when a song won't load, and the sound side of it
private val PlaybackSpec =
    MochyPageSpec(
        sections =
            listOf(
                MochySection(
                    Res.string.settings_section_listening,
                    listOf(
                        MochyRow(P, "resumeOnBluetoothConnect", Res.drawable.mochy_set_bluetooth),
                        MochyRow(P, "pauseOnMute", Res.drawable.mochy_set_volume_off),
                        MochyRow(P, "persistentQueue", Res.drawable.mochy_set_cached),
                        MochyRow(P, "promptContinuePlayback", Res.drawable.mochy_set_cached, subOf = "persistentQueue"),
                        MochyRow(P, "persistentShuffle", Res.drawable.mochy_set_shuffle),
                        MochyRow(P, "skipSilence", Res.drawable.mochy_set_skip_next),
                        MochyRow(P, "skipSilenceInstant", Res.drawable.mochy_set_fast_forward, subOf = "skipSilence"),
                        MochyRow(P, "keepPausedOnSongChange", Res.drawable.mochy_set_play),
                        MochyRow(P, "rememberShuffleRepeat", Res.drawable.mochy_set_repeat),
                        MochyRow(P, "legacyShuffle", Res.drawable.mochy_set_shuffle),
                        MochyRow(P, "sponsorBlockEnabled", Res.drawable.mochy_set_skip_next),
                        MochyRow(P, "seekDuration", Res.drawable.mochy_set_fast_forward),
                        MochyRow(P, "seekExtraSeconds", Res.drawable.mochy_set_fast_forward),
                        MochyRow(P, "keepScreenOnMode", Res.drawable.mochy_set_smartphone),
                    ),
                ),
                MochySection(
                    Res.string.settings_section_failures,
                    listOf(MochyRow(P, "autoSkipOnError", Res.drawable.mochy_set_skip_next)),
                ),
                MochySection(
                    Res.string.settings_sound,
                    listOf(
                        MochyRow(P, "audioQuality", Res.drawable.mochy_set_graphic_eq),
                        MochyGroupRows(P, "sound", Res.drawable.mochy_set_volume_up),
                        MochyRow(P, "varispeed", Res.drawable.mochy_set_speed),
                        MochyRow(P, "audioTrackPlaybackParams", Res.drawable.mochy_set_speed),
                        MochyRow(P, "playbackSpeed", Res.drawable.mochy_set_speed),
                        MochyRow(P, "playbackPitch", Res.drawable.mochy_set_music_note),
                    ),
                ),
                MochySection(
                    Res.string.queue,
                    listOf(
                        MochyRow(P, "preventDuplicates", Res.drawable.mochy_set_queue_music),
                        MochyRow(P, "autoLoadMore", Res.drawable.mochy_set_queue_music),
                        MochyRow(P, "classicRecommendations", Res.drawable.mochy_set_library_music),
                    ),
                ),
                MochySection(
                    Res.string.settings_cast_title,
                    listOf(
                        MochyRow(P, "castEnabled", Res.drawable.mochy_set_cast),
                        MochyRow(P, "castVolumeStep", Res.drawable.mochy_set_volume_up, subOf = "castEnabled"),
                    ),
                ),
                MochySection(
                    Res.string.downloads,
                    listOf(
                        MochyRow(P, "autoDownloadOnLike", Res.drawable.mochy_set_download),
                        MochyRow(P, "autoDownloadSavedCollections", Res.drawable.mochy_set_download),
                    ),
                ),
                MochySection(
                    Res.string.settings_related,
                    listOf(
                        MochyPageLink(
                            SettingsPageId.TRANSITION,
                            Res.drawable.mochy_set_linear_scale,
                            Res.string.settings_transition_link_desc,
                        ),
                        MochyPageLink(SettingsPageId.AUDIO_LAB, Res.drawable.mochy_set_graphic_eq),
                        MochyPageLink(SettingsPageId.EQUALIZER, Res.drawable.mochy_set_tune),
                    ),
                ),
            ),
        covers = listOf(P, SettingsPageId.PLAYER_QUEUE),
    )

// settings > now playing: what the player shows, its lyrics, its videos and its canvas
private val NowPlayingSpec =
    MochyPageSpec(
        sections =
            listOf(
                MochySection(
                    Res.string.settings_on_the_player,
                    listOf(
                        MochyPageLink(SettingsPageId.PLAYER_STYLE, Res.drawable.mochy_set_play),
                        MochyPageLink(SettingsPageId.MINI_PLAYER_STYLE, Res.drawable.mochy_set_play),
                        MochyRow(A, "customizePlayer", Res.drawable.mochy_set_tune),
                        MochyRow(A, "playVideosInMiniPlayer", Res.drawable.mochy_set_slow_motion_video),
                        MochyRow(A, "swipeSensitivity", Res.drawable.mochy_set_swipe),
                        MochyRow(L, "showLyricsOnPlayer", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "hideStatusBar", Res.drawable.mochy_set_tune),
                    ),
                ),
                MochySection(
                    Res.string.settings_section_lyrics,
                    listOf(
                        MochyGroupRows(L, "lyricsAdvanced", Res.drawable.mochy_set_translate),
                        MochyRow(L, "preferWordSyncedLyrics", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "globalLyricsOffset", Res.drawable.mochy_set_timer),
                        MochyRow(L, "lyricsClickToSeek", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "lyricsAutoScroll", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "lyricsActiveByRealTiming", Res.drawable.mochy_set_timer),
                        MochyRow(L, "respectAgentPositioning", Res.drawable.mochy_set_person),
                    ),
                ),
                MochySection(
                    Res.string.lyrics_display,
                    listOf(
                        MochyRow(L, "lyricsPosition", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "activeLyricsPosition", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "lyricsColor", Res.drawable.mochy_set_palette),
                        MochyRow(L, "lyricsAnimation", Res.drawable.mochy_set_sparks),
                        MochyRow(L, "lyricsTextSize", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "lyricsLineSpacing", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "subtitleTextSize", Res.drawable.mochy_set_lyrics),
                        MochyRow(L, "lyricsProgressiveBlur", Res.drawable.mochy_set_sparks),
                        MochyRow(L, "lyricsPlusLighter", Res.drawable.mochy_set_sparks),
                    ),
                ),
                MochySection(
                    Res.string.settings_section_videos,
                    listOf(
                        MochyRow(SettingsPageId.PLAYER_STYLE, "musicVideoPlaybackEnabled", Res.drawable.mochy_set_slow_motion_video),
                        MochyRow(
                            SettingsPageId.PLAYER_STYLE,
                            "musicVideoQuality",
                            Res.drawable.mochy_set_slow_motion_video,
                            subOf = "musicVideoPlaybackEnabled",
                        ),
                    ),
                ),
                MochySection(
                    Res.string.settings_section_canvas,
                    listOf(
                        MochyRow(SettingsPageId.PLAYER_ARTWORK, "playerCanvasEnabled", Res.drawable.mochy_set_sparks),
                        MochyGroupRows(SettingsPageId.PLAYER_ARTWORK, "animatedCovers", Res.drawable.mochy_set_sparks),
                        MochyRow(SettingsPageId.PLAYER_ARTWORK, "youtubeCanvasQuality", Res.drawable.mochy_set_slow_motion_video),
                        MochyRow(SettingsPageId.PLAYER_ARTWORK, "youtubeCanvasMode", Res.drawable.mochy_set_slow_motion_video),
                    ),
                ),
                MochySection(
                    Res.string.settings_artist_sources,
                    listOf(MochyGroupRows(SettingsPageId.PLAYER_ARTWORK, "artistArtwork", Res.drawable.mochy_set_person)),
                ),
            ),
        covers = listOf(L, SettingsPageId.PLAYER_ARTWORK),
    )

// settings > app behaviour: the app's language and updates, what it shows, what it keeps and what it reports
private val AppBehaviourSpec =
    MochyPageSpec(
        sections =
            listOf(
                MochySection(
                    Res.string.general_app_settings,
                    listOf(
                        MochyPageLink(SettingsPageId.MOCHY_PROFILE, Res.drawable.mochy_set_person),
                        MochyRow(C, "appLanguage", Res.drawable.mochy_set_translate),
                        MochyPageLink(SettingsPageId.UPDATES, Res.drawable.mochy_set_update),
                        MochyRow(A, "hideSettingsDescriptions", Res.drawable.mochy_set_info),
                        MochyRow(A, "haptics", Res.drawable.mochy_set_smartphone),
                        MochyPageLink(SettingsPageId.SUPPORTED_LINKS, Res.drawable.mochy_set_link),
                    ),
                ),
                MochySection(
                    Res.string.settings_localization,
                    listOf(
                        MochyGroupRows(C, "youtubeAccount", Res.drawable.mochy_set_person),
                        MochyRow(C, "contentLanguage", Res.drawable.mochy_set_language),
                        MochyRow(C, "contentCountry", Res.drawable.mochy_set_language),
                        MochyGroupRows(C, "sharing", Res.drawable.mochy_set_share),
                    ),
                ),
                MochySection(
                    Res.string.settings_section_library_privacy,
                    listOf(
                        MochyRow(V, "historyPlaylists", Res.drawable.mochy_set_history),
                        MochyRow(V, "queueOfflineHistory", Res.drawable.mochy_set_sync),
                        MochyRow(V, "historyDuration", Res.drawable.mochy_set_history),
                        MochyRow(V, "hideRecentSearches", Res.drawable.mochy_set_history),
                        MochyRow(V, "clearSearchHistory", Res.drawable.mochy_set_delete),
                        MochyRow(V, "blockScreenshots", Res.drawable.mochy_set_lock),
                    ),
                ),
                MochySection(
                    Res.string.history,
                    listOf(MochyGroupRows(V, "history", Res.drawable.mochy_set_history)),
                ),
                MochySection(
                    Res.string.content_filters_filtering,
                    listOf(
                        MochyRow(C, "hideExplicit", Res.drawable.mochy_set_explicit),
                        MochyRow(C, "explicitOnly", Res.drawable.mochy_set_explicit),
                        MochyRow(C, "blockAiArtists", Res.drawable.mochy_set_person),
                        MochyRow(C, "hideVideoSongs", Res.drawable.mochy_set_slow_motion_video),
                        MochyRow(C, "hideNonMusicalVideos", Res.drawable.mochy_set_slow_motion_video),
                        MochyRow(C, "hideShortSongs", Res.drawable.mochy_set_timer),
                        MochyRow(C, "minimumDuration", Res.drawable.mochy_set_timer, subOf = "hideShortSongs"),
                        MochyRow(C, "hidePodcasts", Res.drawable.mochy_set_music_note),
                    ),
                ),
                MochySection(
                    Res.string.privacy_blocklists,
                    listOf(MochyGroupRows(V, "blocklists", Res.drawable.mochy_set_lock)),
                ),
                MochySection(
                    Res.string.privacy_diagnostics,
                    listOf(MochyGroupRows(V, "diagnostics", Res.drawable.mochy_set_bug_report)),
                ),
                MochySection(
                    Res.string.settings_related,
                    listOf(
                        MochyPageLink(SettingsPageId.MOCHY_PLAYBACK, Res.drawable.mochy_set_play),
                        MochyPageLink(SettingsPageId.MOCHY_NOW_PLAYING, Res.drawable.mochy_set_lyrics),
                        MochyPageLink(SettingsPageId.ABOUT, Res.drawable.mochy_set_info),
                        MochyPageLink(SettingsPageId.DEVTOOLS, Res.drawable.mochy_set_bug_report),
                    ),
                ),
            ),
        covers = listOf(C, V),
    )

// settings > customisation: the app's theme and layout, the player's look, gestures and the home screen
private val CustomisationSpec =
    MochyPageSpec(
        sections =
            listOf(
                MochySection(
                    Res.string.appearance_theme,
                    listOf(MochyGroupRows(A, "theme", Res.drawable.mochy_set_palette)),
                ),
                MochySection(
                    Res.string.appearance_player,
                    listOf(
                        MochyGroupRows(
                            A,
                            "playerAppearance",
                            Res.drawable.mochy_set_play,
                            except = setOf("customizePlayer", "playVideosInMiniPlayer", "swipeSensitivity"),
                        ),
                    ),
                ),
                MochySection(
                    Res.string.appearance_navigation_tabs,
                    listOf(MochyGroupRows(A, "tabs", Res.drawable.mochy_set_tune)),
                ),
                MochySection(
                    Res.string.settings_layouts,
                    listOf(MochyGroupRows(A, "desktopLayouts", Res.drawable.mochy_set_tune)),
                ),
                MochySection(
                    Res.string.library,
                    listOf(
                        MochyGroupRows(A, "libraryGrid", Res.drawable.mochy_set_album),
                        MochyGroupRows(C, "autoPlaylists", Res.drawable.mochy_set_queue_music),
                    ),
                ),
                MochySection(
                    Res.string.display_density,
                    listOf(MochyGroupRows(A, "displayDensity", Res.drawable.mochy_set_image)),
                ),
                MochySection(
                    Res.string.song_gestures,
                    listOf(MochyGroupRows(A, "gestures", Res.drawable.mochy_set_swipe)),
                ),
                MochySection(
                    Res.string.settings_home_sections,
                    listOf(MochyGroupRows(C, "homeSections", Res.drawable.mochy_set_library_music)),
                ),
                MochySection(
                    Res.string.appearance_misc,
                    listOf(
                        MochyGroupRows(A, "misc", Res.drawable.mochy_set_settings, except = setOf("haptics", "hideSettingsDescriptions")),
                    ),
                ),
            ),
        covers = listOf(A),
    )

// settings > devices: the phone's links to other screens, speakers and cars
private val DevicesSpec =
    MochyPageSpec(
        sections =
            listOf(
                MochySection(
                    null,
                    listOf(
                        MochyPageLink(SettingsPageId.DEVICE_CONNECT, Res.drawable.mochy_set_cast_connected),
                        MochyPageLink(SettingsPageId.ANDROID_AUTO, Res.drawable.mochy_set_smartphone),
                    ),
                ),
                MochySection(
                    Res.string.settings_cast_title,
                    listOf(
                        MochyRow(P, "castEnabled", Res.drawable.mochy_set_cast),
                        MochyRow(P, "castVolumeStep", Res.drawable.mochy_set_volume_up, subOf = "castEnabled"),
                    ),
                ),
            ),
    )
