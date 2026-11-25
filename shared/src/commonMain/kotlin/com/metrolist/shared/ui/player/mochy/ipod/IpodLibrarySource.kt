// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.player.mochy.ipod

import com.metrolist.shared.constants.ArtistSortType
import com.metrolist.shared.constants.PlaylistSortType
import com.metrolist.shared.constants.SongSortType
import com.metrolist.shared.db.MusicDatabase
import com.metrolist.shared.db.entities.Album
import com.metrolist.shared.db.entities.Song
import com.metrolist.shared.db.entities.toSongItem
import com.metrolist.shared.db.loadStoredAlbum
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.ipod_album_load_failed
import com.metrolist.shared.generated.resources.ipod_no_albums
import com.metrolist.shared.generated.resources.ipod_no_artists
import com.metrolist.shared.generated.resources.ipod_no_playlists
import com.metrolist.shared.generated.resources.ipod_no_songs
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.state.GlobalYouTubeRepository
import com.metrolist.shared.utils.sizedArtworkUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.mp.KoinPlatform
import kotlin.concurrent.Volatile

internal class IpodLibrarySource(
    private val scope: CoroutineScope,
    private val database: MusicDatabase = KoinPlatform.getKoin().get(),
) {
    private val cacheLock = Mutex()
    private val cache =
        mutableMapOf<IpodPage, IpodList>(
            IpodPage.Main to IpodMainList,
            IpodPage.Music to IpodMusicList,
        )

    @Volatile
    var cachedCoverFlow: List<IpodAlbum>? = null
        private set

    @Volatile
    var cachedCovers: List<String>? = null
        private set

    init {
        scope.launch(Dispatchers.Default) {
            runCatching { covers() }
            runCatching { coverFlow() }
            val playlists = runCatching { load(IpodPage.Playlists) }.getOrNull()
            runCatching { load(IpodPage.Artists) }
            runCatching { load(IpodPage.Albums) }
            runCatching { load(IpodPage.LikedSongs) }
            runCatching { load(IpodPage.Songs) }
            playlists?.rows?.forEach { row ->
                val open = row.pick as? IpodPick.Open ?: return@forEach
                runCatching { load(open.page) }
            }
        }
    }

    fun cached(page: IpodPage): IpodList? =
        when (page) {
            IpodPage.Main -> IpodMainList
            IpodPage.Music -> IpodMusicList
            else -> cache[page]
        }

    suspend fun load(page: IpodPage): IpodList =
        withContext(Dispatchers.Default) {
            cacheLock.withLock { cache[page] }?.let { return@withContext it }
            val built = buildList(page)
            cacheLock.withLock { cache[page] = built }
            built
        }

    fun refreshAsync(page: IpodPage) {
        if (page == IpodPage.Main || page == IpodPage.Music || page == IpodPage.NowPlaying || page == IpodPage.CoverFlow) return
        scope.launch(Dispatchers.Default) {
            runCatching {
                val built = buildList(page)
                cacheLock.withLock { cache[page] = built }
            }
        }
    }

    private suspend fun buildList(page: IpodPage): IpodList {
        val dao = database.dao()
        return when (page) {
            IpodPage.Main -> IpodMainList
            IpodPage.Music -> IpodMusicList
            IpodPage.NowPlaying, IpodPage.CoverFlow -> IpodList.Loading
            IpodPage.Playlists ->
                withArt(
                    rows =
                        dao.playlists(PlaylistSortType.NAME, descending = false).first().map { playlist ->
                            IpodRow(
                                key = playlist.id,
                                title = playlist.title,
                                pick = IpodPick.Open(IpodPage.Playlist(playlist.id, playlist.title)),
                                subtitleCount = playlist.songCount,
                                art = playlist.thumbnailUrl?.let { sizedArtworkUrl(it, ROW_PX) },
                            )
                        },
                    emptyRes = Res.string.ipod_no_playlists,
                )
            IpodPage.Artists ->
                withArt(
                    rows =
                        dao.artists(ArtistSortType.NAME, descending = false).first().map { artist ->
                            IpodRow(
                                key = artist.id,
                                title = artist.title,
                                pick = IpodPick.Open(IpodPage.Artist(artist.id, artist.title)),
                                subtitleCount = artist.songCount,
                                art = artist.thumbnailUrl?.let { sizedArtworkUrl(it, ROW_PX) },
                                roundArt = true,
                            )
                        },
                    emptyRes = Res.string.ipod_no_artists,
                )
            IpodPage.Albums ->
                withArt(
                    rows =
                        albums().map { album ->
                            IpodRow(
                                key = album.id,
                                title = album.title,
                                pick = IpodPick.Open(IpodPage.Album(album.id, album.title)),
                                subtitle = album.artists.joinToString { a -> a.name }.ifBlank { null },
                                art = album.thumbnailUrl?.let { sizedArtworkUrl(it, ROW_PX) },
                            )
                        },
                    emptyRes = Res.string.ipod_no_albums,
                )
            IpodPage.Songs -> songs(dao.songs(SongSortType.NAME, descending = false).first().map(Song::toSongItem), art = true)
            IpodPage.LikedSongs ->
                songs(dao.likedSongs(SongSortType.NAME, descending = false).first().map(Song::toSongItem), art = true)
            is IpodPage.Playlist -> songs(dao.playlistSongs(page.id).first().map { it.toSongItem() }, art = true)
            is IpodPage.Artist -> songs(dao.artistSongsByNameAsc(page.id).first().map(Song::toSongItem), art = true)
            is IpodPage.Album -> albumTracks(page.id)
        }
    }

    suspend fun covers(): List<String> =
        withContext(Dispatchers.Default) {
            cachedCovers?.let { return@withContext it }
            val dao = database.dao()
            (dao.albumsByCreateDateAsc().first().reversed() + dao.albumsLikedByCreateDateAsc().first().reversed())
                .mapNotNull { it.thumbnailUrl?.let { url -> sizedArtworkUrl(url, COVER_PX) } }
                .distinct()
                .take(MAX_COVERS)
                .also { cachedCovers = it }
        }

    suspend fun coverFlow(): List<IpodAlbum> =
        withContext(Dispatchers.Default) {
            cachedCoverFlow?.let { return@withContext it }
            albums()
                .map {
                    IpodAlbum(
                        id = it.id,
                        title = it.title,
                        artist = it.artists.joinToString { a -> a.name },
                        cover = it.thumbnailUrl?.let { url -> sizedArtworkUrl(url, COVER_PX) },
                    )
                }.also { cachedCoverFlow = it }
        }

    suspend fun shuffledLibrary(): List<SongItem> =
        withContext(Dispatchers.Default) {
            database.dao().songs(SongSortType.NAME, descending = false).first().shuffled().map(Song::toSongItem)
        }

    private suspend fun albums(): List<Album> {
        val dao = database.dao()
        val combined = (dao.albumsLikedByCreateDateAsc(includeSaved = true).first() + dao.albumsByNameAsc(includeBookmarked = true).first())
        return combined.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    private suspend fun albumTracks(id: String): IpodList {
        val stored = database.loadStoredAlbum(id)?.songs.orEmpty()
        if (stored.isNotEmpty()) return songs(stored, art = false)
        val fetched =
            withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                runCatching { GlobalYouTubeRepository.instance.getAlbum(id) }.getOrNull()
            }
        val tracks = fetched?.songs ?: database.loadStoredAlbum(id)?.songs.orEmpty()
        if (tracks.isEmpty()) {
            return IpodList(listOf(IpodRow("empty", "", null, titleRes = Res.string.ipod_album_load_failed)))
        }
        return songs(tracks, art = false)
    }

    private fun withArt(
        rows: List<IpodRow>,
        emptyRes: StringResource,
    ): IpodList =
        if (rows.isEmpty()) {
            IpodList(listOf(IpodRow("empty", "", null, titleRes = emptyRes)))
        } else {
            IpodList(rows, art = true)
        }

    private fun songs(
        songs: List<SongItem>,
        art: Boolean,
    ): IpodList {
        if (songs.isEmpty()) return IpodList(listOf(IpodRow("empty", "", null, titleRes = Res.string.ipod_no_songs)))
        val rows =
            songs.mapIndexed { index, song ->
                IpodRow(
                    key = "${song.id}#$index",
                    title = song.title,
                    pick = IpodPick.Play(index),
                    subtitle = if (art) song.artists.joinToString { it.name }.ifBlank { null } else null,
                    art = if (art) (song.previewThumbnailUrl ?: song.thumbnail)?.let { sizedArtworkUrl(it, ROW_PX) } else null,
                )
            }
        return IpodList(rows, songs, art = art)
    }

    private companion object {
        const val COVER_PX = 400
        const val ROW_PX = 120
        const val MAX_COVERS = 24
        const val FETCH_TIMEOUT_MS = 10_000L
    }
}
