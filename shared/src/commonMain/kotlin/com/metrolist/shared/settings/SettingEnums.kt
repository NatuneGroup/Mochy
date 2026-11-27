/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.settings

import kotlinx.serialization.Serializable

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO,
    PURE_BLACK,
}

enum class KeepScreenOnMode {
    NEVER,
    PLAYER,
    LYRICS,
    PLAYER_AND_LYRICS,
    ;

    fun keepsAwake(
        isPlaying: Boolean,
        lyricsVisible: Boolean,
    ): Boolean =
        isPlaying &&
            when (this) {
                NEVER -> false
                PLAYER -> !lyricsVisible
                LYRICS -> lyricsVisible
                PLAYER_AND_LYRICS -> true
            }
}

enum class CrossfadeStyle {
    AUTO,
    MANUAL,
}

// the length Auto Crossfade blends each song over, in seconds
const val AUTO_CROSSFADE_SECONDS = 5f

enum class AppLayout {
    EXPRESS,
    COMFORTABLE,
    FOCUSED,
    DYNAMIC,
}

enum class SliderStyle {
    DEFAULT,
    WAVY,
    SLIM,
    SQUIGGLY,
    LINEAR,
    LINEAR_WAVY,
}

enum class PlayerThumbnailStyle {
    COMPACT,
    FULLSCREEN,
    CANVAS,
}

enum class PlayerBackgroundStyle {
    BLURRED_ARTWORK,
    SOLID,
    GRADIENT,
    ANIMATED_GRADIENT,
    LIQUID,
}

enum class ThemePaletteStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    NEUTRAL,
    CONTENT,
}

enum class LibraryViewType {
    LIST,
    GRID,
}

@Serializable
enum class LibraryFilterType(
    val storedValue: Int,
) {
    DEFAULT(0),
    ALL(1),
    TRACKS(2),
    PLAYLISTS(3),
    ARTISTS(5),
    ALBUMS(6),
    PODCASTS(7),
    ;

    companion object {
        // Keep stored IDs stable and map the removed Owned filter to Playlists.
        fun fromStoredValue(value: Int): LibraryFilterType? = if (value == 4) PLAYLISTS else entries.firstOrNull { it.storedValue == value }
    }
}

enum class LibraryContentSort {
    RECENTLY_ADDED,
    NAME,
    LAST_UPDATED,
    MANUAL,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAYTIME,
    DOWNLOADED,
}

enum class FontOption {
    SYSTEM,
    INTER,
    GOOGLE_SANS,
    MAPLE_MONO,
}

enum class AnimatedCoverResolution {
    LOW_360,
    MEDIUM_720,
    HIGH_1080,
    ORIGINAL,
}

enum class YouTubeCanvasMode {
    SHORT_CLIPS,
    FULL_VIDEO,
}

enum class LoudnessLevel(
    val targetLufs: Float,
) {
    AGGRESSIVE(-7f),
    LOUD(-11f),
    BALANCED(-14f),
    QUIET(-19f),
}

enum class MusicVideoQuality {
    AUTO,
    P2160,
    P1440,
    P1080,
    P720,
    P480,
}

val MusicVideoQuality.targetHeight: Int?
    get() =
        when (this) {
            MusicVideoQuality.AUTO -> null
            MusicVideoQuality.P2160 -> 2160
            MusicVideoQuality.P1440 -> 1440
            MusicVideoQuality.P1080 -> 1080
            MusicVideoQuality.P720 -> 720
            MusicVideoQuality.P480 -> 480
        }

enum class PlayerType {
    MODERN,
    VINTAGE,
    CLASSIC,
    MOCHY,
    TURNTABLE,
    IPOD,

    // Mochy's alternate styles: the cover as a great circle over the lyrics, the song as time, the lyrics on stage
    POSTER,
    TIMELINE,
    LYRICS_STAGE,

    // Mochy's bedside clock as the player, and the song's video filling the screen sideways
    BEDSIDE,
    VIDEO,

    // Sora's port of namida's player
    NAMIDA,
}

// the flagship's bottom: Mochy's lyric and queue cards one behind the other, or the phone's volume with the
// lyrics and queue buttons under it
enum class FlagshipBottomBarStyle {
    STACK,
    VOLUME,
}

// the flagship's cover: the square card, the artwork bled to both screen edges, or a spinning record
enum class PlayerCoverStyle {
    SQUARED,
    EDGE_TO_EDGE,
    DISC,

    // the reference's disc looks; appended so the saved ordinals above stay valid
    DISC_SMALL_FULL,
    DISC_SMALL_LABEL,
    DISC_BIG_FULL,
    DISC_BIG_LABEL,
    DISC_ALBUM,
}

enum class IpodTheme {
    SILVER,
    BLACK,
    U2,
    TRUE_DARK,
}

enum class IpodWheelColor {
    THEME,
    WHITE,
    BLACK,
    RED,
}

enum class MiniPlayerType {
    MODERN,
    VINTAGE,
    EXPRESSIVE,
    MOCHY,
}

enum class DensityScale(
    val value: Float,
) {
    MASSIVE(2.00f),
    HUGE(1.75f),
    EXTRA_LARGE(1.50f),
    LARGE(1.25f),
    SLIGHTLY_LARGE(1.15f),
    NATIVE(1f),
    SLIGHTLY_COMPACT(0.85f),
    COMPACT(0.75f),
    VERY_COMPACT(0.65f),
    ULTRA_COMPACT(0.55f),
    ;

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: NATIVE
    }
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class SongShareLink { YOUTUBE_MUSIC, SONG_LINK }
