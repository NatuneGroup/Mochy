// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.mochy

import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.continue_action
import com.metrolist.shared.generated.resources.mochy_hero_back_in_rotation
import com.metrolist.shared.generated.resources.mochy_hero_because_you_listen
import com.metrolist.shared.generated.resources.mochy_hero_playlist
import com.metrolist.shared.generated.resources.mochy_hero_you_may_also_like
import com.metrolist.shared.generated.resources.quick_picks
import com.metrolist.shared.generated.resources.recently_played
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.model.canonicalHomeItemId
import com.metrolist.shared.model.isPodcast
import com.metrolist.shared.viewmodels.HomeViewModel.HomeUiState
import org.jetbrains.compose.resources.StringResource

internal data class MochyHeroItem(
    val item: YTItem,
    val reason: StringResource,
    val artistSeed: ArtistItem? = null,
)

internal fun mochyHeroItems(
    feed: HomeUiState,
    currentSong: SongItem?,
    includePersonalHistory: Boolean,
    isItemEnabled: (YTItem) -> Boolean,
    fallbackItems: List<YTItem> = emptyList(),
): List<MochyHeroItem> {
    fun YTItem.hasCover(): Boolean = !thumbnail.isNullOrBlank() && !isUnavailable && isItemEnabled(this)

    fun SongItem.isMusicPick(): Boolean = !isPodcast && (includePersonalHistory || !isLocal) && hasCover()
    val result = mutableListOf<MochyHeroItem>()
    val seen = mutableSetOf<String>()

    fun add(
        item: YTItem?,
        reason: StringResource,
        artistSeed: ArtistItem? = null,
    ) {
        if (item == null || !item.hasCover() || item.isPodcast || !seen.add(canonicalHomeItemId(item))) return
        result.add(MochyHeroItem(item, reason, artistSeed))
    }

    if (includePersonalHistory) add(currentSong?.takeIf { it.isMusicPick() }, Res.string.continue_action)

    val discoveries = feed.quickPicks.filter { it.isMusicPick() }.take(4)
    val related =
        feed.similarRecommendations
            .filterNot { it.browseId?.startsWith("FEmusic_library", ignoreCase = true) == true }
            .mapNotNull { row ->
                val item =
                    row.items.firstOrNull { it is SongItem && it.isMusicPick() }
                        ?: row.items.firstOrNull { candidate ->
                            when (candidate) {
                                is ArtistItem, is AlbumItem -> candidate.hasCover()
                                is PlaylistItem -> !candidate.isLocal && candidate.hasCover()
                                else -> false
                            }
                        }
                item?.let { it to row.artist }
            }.take(4)
    for (index in 0 until maxOf(discoveries.size, related.size)) {
        add(discoveries.getOrNull(index), Res.string.mochy_hero_you_may_also_like)
        related.getOrNull(index)?.let { (item, artist) -> add(item, Res.string.mochy_hero_because_you_listen, artist) }
    }

    if (includePersonalHistory) {
        add(
            feed.forgottenFavorites.firstOrNull { it.isMusicPick() && canonicalHomeItemId(it) !in seen },
            Res.string.mochy_hero_back_in_rotation,
        )
    }
    feed.communityPlaylist
        .asSequence()
        .filter { !it.isLocal && it.hasCover() && canonicalHomeItemId(it) !in seen }
        .take(2)
        .forEach { add(it, Res.string.mochy_hero_playlist) }

    if (includePersonalHistory && result.size < 5) {
        (feed.keepListening + feed.recentlyPlayed)
            .asSequence()
            .filter { it.isMusicPick() && canonicalHomeItemId(it) !in seen }
            .distinctBy(::canonicalHomeItemId)
            .take(5 - result.size)
            .forEach { add(it, Res.string.recently_played) }
    }
    if (result.size < 5) {
        feed.trending
            .asSequence()
            .filter { it.isMusicPick() && canonicalHomeItemId(it) !in seen }
            .distinctBy(::canonicalHomeItemId)
            .take(5 - result.size)
            .forEach { add(it, Res.string.quick_picks) }
    }

    if (result.isEmpty()) {
        fun YTItem.isPlayableStarter(
            allowLocal: Boolean,
            requireCover: Boolean,
        ): Boolean {
            if (isUnavailable || isPodcast || !isItemEnabled(this)) return false
            if (requireCover && thumbnail.isNullOrBlank()) return false
            if (id.startsWith("FEmusic_library", ignoreCase = true)) return false
            return when (this) {
                is SongItem -> allowLocal || !isLocal
                is PlaylistItem -> allowLocal || !isLocal
                is AlbumItem, is ArtistItem -> true
                else -> false
            }
        }

        fun addFallback(
            item: YTItem?,
            reason: StringResource,
            allowLocal: Boolean,
            requireCover: Boolean,
        ) {
            if (result.size >= 8) return
            if (item == null || !item.isPlayableStarter(allowLocal, requireCover)) return
            if (!seen.add(canonicalHomeItemId(item))) return
            result.add(MochyHeroItem(item, reason))
        }

        fun reasonFor(item: YTItem): StringResource =
            if (item is PlaylistItem) {
                Res.string.mochy_hero_playlist
            } else {
                Res.string.quick_picks
            }

        val onlineFallbackPool =
            buildList {
                addAll(feed.newReleases)
                addAll(feed.albumCharts)
                addAll(feed.speedDialItems)
                addAll(feed.sections.flatMap { it.items })
                addAll(feed.data.orEmpty().flatMap { it.items })
                addAll(fallbackItems)
            }
        onlineFallbackPool.forEach { item ->
            addFallback(item, reasonFor(item), allowLocal = false, requireCover = true)
        }

        if (result.isEmpty()) {
            val localFallbackPool =
                buildList {
                    addAll(feed.speedDialItems)
                    addAll(feed.quickPicks)
                    addAll(feed.keepListening)
                    addAll(feed.recentlyPlayed)
                    addAll(feed.forgottenFavorites)
                    addAll(feed.communityPlaylist)
                    addAll(feed.sections.flatMap { it.items })
                    addAll(feed.data.orEmpty().flatMap { it.items })
                    addAll(fallbackItems)
                }
            addFallback(currentSong, Res.string.continue_action, allowLocal = true, requireCover = true)
            localFallbackPool.forEach { item ->
                addFallback(item, reasonFor(item), allowLocal = true, requireCover = true)
            }
            if (result.isEmpty()) {
                addFallback(currentSong, Res.string.continue_action, allowLocal = true, requireCover = false)
                localFallbackPool.forEach { item ->
                    addFallback(item, reasonFor(item), allowLocal = true, requireCover = false)
                }
            }
        }
    }

    return result.take(8)
}
