// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.modules

import com.metrolist.shared.model.AlbumTiny
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.youtube.playback.PlaybackNetworkStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

class MochyDisplayMetadata internal constructor(
    private val songId: String,
    private val metadata: TrackMetadata,
) {
    fun apply(song: SongItem): SongItem {
        if (songId != song.id) return song
        val baseArtists = song.canonicalArtists ?: song.artists
        val artistName = metadata.artist?.trim()?.takeIf(String::isNotEmpty)
        val artists =
            if (song.customArtist.isNullOrBlank() && artistName != null && baseArtists.none { !it.id.isNullOrBlank() }) {
                listOf(ArtistTiny(id = null, name = artistName))
            } else {
                song.artists
            }
        val title = metadata.title?.trim()?.takeIf { it.isNotEmpty() && song.customTitle.isNullOrBlank() }
        val album = metadata.album?.trim()?.takeIf(String::isNotEmpty)
        return song.copy(
            title = title ?: song.title,
            artists = artists,
            album = song.album?.takeIf { !it.id.isNullOrBlank() } ?: album?.let { AlbumTiny(id = null, name = it) } ?: song.album,
            thumbnail = metadata.artworkUrl?.takeIf { it.isMochyHttpUrl() } ?: song.thumbnail,
            canonicalTitle = song.canonicalTitle ?: song.title,
            canonicalArtists = baseArtists,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<SongItem?>.mochyDisplayMetadata(): Flow<MochyDisplayMetadata?> =
    flow {
        val registry = MochyModuleRegistry.shared
        val tracks = map { song -> song?.takeIf { it.canUseMochyModules }?.let { it.id to it.toMochyTrackQuery() } }.distinctUntilChanged()
        val modules =
            registry.entries
                .map { entries ->
                    entries.filter { it.active && it.adds(ModuleFeature.Metadata) }
                }.distinctUntilChanged()
        val online = PlaybackNetworkStateStore.state.map { it.connected }.distinctUntilChanged()
        combine(tracks, modules, online) { track, entries, connected -> track.takeIf { connected && entries.isNotEmpty() } to entries }
            .transformLatest { (track, entries) ->
                emit(null)
                if (track != null) {
                    val (songId, query) = track
                    registry.fetchModuleMetadata(query, entries)?.let { emit(MochyDisplayMetadata(songId, it)) }
                }
            }.collect { emit(it) }
    }
