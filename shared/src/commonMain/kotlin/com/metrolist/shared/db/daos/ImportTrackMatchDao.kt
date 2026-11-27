package com.metrolist.shared.db.daos

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.metrolist.shared.db.entities.ImportTrackMatch
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTrackMatchDao {
    @Query(
        "SELECT DISTINCT import_track_match.* FROM import_track_match JOIN playlist_song_map ON playlist_song_map.playlistId = import_track_match.playlistId AND playlist_song_map.songId = import_track_match.songId WHERE import_track_match.playlistId = :playlistId AND provider = :provider",
    )
    suspend fun importTrackMatches(
        playlistId: String,
        provider: String,
    ): List<ImportTrackMatch>

    @Upsert
    suspend fun upsert(matches: List<ImportTrackMatch>)

    // every playlist an import filled, with the service it came from, for the Library's Imported tab
    @Query(
        "SELECT DISTINCT import_track_match.playlistId AS playlistId, import_track_match.provider AS provider, playlist.name AS name, playlist.thumbnailUrl AS thumbnailUrl FROM import_track_match JOIN playlist ON playlist.id = import_track_match.playlistId ORDER BY playlist.name",
    )
    fun importedPlaylists(): Flow<List<ImportedPlaylist>>
}

data class ImportedPlaylist(
    val playlistId: String,
    val provider: String,
    val name: String,
    val thumbnailUrl: String?,
)
