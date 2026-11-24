# Mochy on the Metrolist base

The separate integration checkout starts at Metrolist `af3d05339d74b342c0a49af308eb81c33d380deb`. The Mochy repository and its uncommitted multiplatform work remain a visual and feature reference.

## Desktop design

Metrolist puts window actions and search at the top, navigation by the content, and transport at the bottom or in the title bar depending on the layout preference. It owns window resizing, keyboard shortcuts, collection navigation, queues, lyrics and video. Mochy's reference has black surfaces, quieter navigation, prominent artwork and a rounded inset mini-player.

The new compact rail uses Mochy-style tiles with the platform's existing navigation and sidebar editor. The bottom player uses an inset surface with Mochy's animated glyphs. Home renders a multiplatform Mochy animated hero carousel with a top ambient cover glow drawn from personalized Quick Picks and trending recommendations, using side-by-side peek cards, hover navigation arrows, clickable page dots, 3D press-tilt (`press3D`), and the animated Play/Pause pill on wide desktop windows. Narrow windows keep bottom tabs, and the focused layout can still put the player in the title bar. Queue and lyrics remain accessible through the existing drawers.

## Mobile source parity

Use Mochy's original mobile design as the reference. The source starts at the Flagship mode in `BottomSheetPlayer.kt`, with the controls drawn by `FlagshipTransportPresentation.kt`, `SeekBarStyles.kt`, `PlayerHandle.kt`, and `SongInfoRow.kt`.

Mochy Flagship is the default mobile player style on a fresh install. Its expanded player uses the original three-button proportions and glyphs, the 7 dp seek track that grows under a press or drag, the bending collapse handle, the original heart and chevron vectors, Inter, and the original controls shade over full-screen artwork. The play and skip buttons drive the existing player. Hold-to-change-speed on a local player uses a temporary override and restores the selected speed when the gesture ends or the player closes. Live and remote playback retain their platform handling.

The full mobile interface still needs the original Home, navigation, library and detail pages, bottom card deck and its page transitions, lyrics and queue layouts, player menus, and alternate player styles. Compare actual Android and iOS screenshots against the matching Mochy state before treating a screen as visually matched.

## Feature mapping

| Area | Current integration | Work left for parity |
| --- | --- | --- |
| Themes and typography | Mochy's neutral palette, dark defaults and text spacing feed the app theme. The mobile Flagship player bundles the original Inter fonts. | Using the original font policy throughout the mobile app, theme editor and the other appearance options. |
| Player chrome | The mobile Flagship uses original transport, seek, collapse, and action controls. The compact desktop and default expressive mobile mini-players use the play glyph. | Original mobile artwork transition, card deck, volume, queue, lyrics, overflow and alternate layouts. |
| Home and library | Desktop Home has an artwork-led card. Metrolist continues to provide its own shared feeds, discovery, search and library. | Mochy's phone Home, Jump Back In layout, library cards, playlist and album detail, profiles, and recap presentation. |
| Bedside | The player menu opens a dark clock with localized system time, date, current track and playback. | Mochy's alternate faces, tiles, inactivity dimming and lock-screen behavior. |
| Library transfer | Import from another app recognizes Mochy backups and merges likes, playlists, play history, playlist descriptions, edited song titles and artist text, and lyric offsets. | Mochy-specific settings, account sessions, local files, downloads, pins, artwork files, lyric text and other fields outside the shared import model. |
| Audio and integrations | Keep the base player's queue, video and EQ and the base account, import and scrobbling flows. | Match Mochy Audio Lab, smart CrossMix and stage DSP, video and canvas behavior, lyric providers, sharing and third-party integrations against the existing implementations. |

## Mochy modules

Enabled sources can supply audio for ordinary music tracks. Downloads, local files, video, podcasts, uploads, live streams, Cast, and synchronized playback use their existing paths. If a module cannot resolve or prepare a track, the player falls back to its standard source. Android and desktop accept progressive, HLS and DASH sources. iOS accepts progressive and HLS and skips DASH. Desktop inline DASH manifests need absolute segment URLs or an absolute base URL.

A web module's `look` can set `player` to `flagship`, `modern`, `vintage` or `classic`; `mini` to `expressive`, `modern` or `vintage`; `bar` to `standard`, `compact` or `floating`; and `font` to `inter` or `system`. These overrides apply where the host has the corresponding layout. Disabling the module restores the saved preferences. These keys select existing layouts and fonts. Mochy's custom skin layouts and packaged assets still need their own renderer.

Enabled lyrics modules can supply missing lyrics or improve timing after the built-in providers run. Enabled metadata modules can supply title, artwork and unlinked artist or album information for the current track. The metadata applies to the player display and preserves local edits, linked artists and albums, and library data.

## Verification before publishing

Use GitHub Actions for Android, desktop and iOS compilation and the shared desktop test suite. Check the player on a phone and in a narrow and wide desktop window. Resize while queue or lyrics is open, switch theme, toggle playback from the dock and clock, and verify keyboard and screen-reader access. Compare Android to the Mochy source screenshots. The coverage for backup import includes a fixture with duplicate playlist entries, song edits, two imports of the same file and edits already present in the destination.
