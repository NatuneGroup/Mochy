# Mochy on the Metrolist base

The separate integration checkout starts at Metrolist `af3d05339d74b342c0a49af308eb81c33d380deb`. The Mochy repository and its uncommitted multiplatform work remain a visual and feature reference.

## Android package and release builds

The Android app is `com.natune.mochy`, labelled Mochy, with the Mochy play-triangle launcher mark. Release builds enable R8 and resource shrinking and use the shipped `Mochy` branding; debug builds use the `.debug` suffix and the `Mochy Debug` label.

CI collects `Mochy.apk`, `Mochy-izzy.apk` and `mochy-android-<version>-debug.apk`, then verifies every APK with `apksigner verify --verbose --print-certs`. Release signing uses the `KEYSTORE_*` secrets when configured and otherwise falls back to the published test key in `androidApp/keystore/mochy-test.p12`, so consecutive runs share one signature and update in place. See [handoff.md](handoff.md) for the current release evidence and remaining waves.

## Desktop design

Metrolist puts window actions and search at the top, navigation by the content, and transport at the bottom or in the title bar depending on the layout preference. It owns window resizing, keyboard shortcuts, collection navigation, queues, lyrics and video. Mochy's reference has black surfaces, quieter navigation, prominent artwork and a rounded inset mini-player.

The new compact rail uses Mochy-style tiles with the platform's existing navigation and sidebar editor. The bottom player uses an inset surface with Mochy's animated glyphs. Home renders a multiplatform Mochy animated hero carousel with a top ambient cover glow drawn from personalized Quick Picks and trending recommendations, using side-by-side peek cards, hover navigation arrows, clickable page dots, 3D press-tilt (`press3D`), and the animated Play/Pause pill on wide desktop windows. Narrow windows keep bottom tabs, and the focused layout can still put the player in the title bar. Queue and lyrics remain accessible through the existing drawers.

## Mobile source parity

Use Mochy's original mobile design as the reference. The source starts at the Flagship mode in `BottomSheetPlayer.kt`, with the controls drawn by `FlagshipTransportPresentation.kt`, `SeekBarStyles.kt`, `PlayerHandle.kt`, and `SongInfoRow.kt`.

Mochy Flagship is the default mobile player style on a fresh install. Its expanded player uses the original three-button proportions and glyphs, the 7 dp seek track that grows under a press or drag, the bending collapse handle, the original heart and chevron vectors, Inter, and the original controls shade over full-screen artwork. The play and skip buttons drive the existing player. Hold-to-change-speed on a local player uses a temporary override and restores the selected speed when the gesture ends or the player closes. Live and remote playback retain their platform handling. The portrait bottom bar uses Mochy's animated speaker and volume track and its output, lyrics and queue artwork. Android follows the system media volume during local playback. iOS uses the player volume, and output opens the fork's existing device chooser. A cancelled drag releases the volume control. The lyrics and queue pills open the existing destinations.

Narrow Home uses Mochy's section headings, including its Inter text, 36 dp inset, chevron and full-row press highlight. The mobile song shelves snap three rows at a time and use the original 54 dp artwork, Inter labels and overflow pills. Regular cover shelves use Mochy's 137 or 104 dp tiles, with circular artist artwork and staggered reveals. Items remain visible when the user turns off animations. Mobile Home uses Mochy's full-width artwork, 60 percent portrait height, large landscape artwork and 33 dp play or view pill. The carousel can resume the current queue, show recommendations with the artist who inspired them, open featured playlists and bring back tracks from listening history. Desktop keeps its wide card layout and uses online picks. The original starter flow, personal playlist picks, Surprise reel, Speed Dial hero, cover video, Listen Now and Hop In layouts remain open.

The player card-to-page transition depends on the card and destination sharing artwork and lyric positions. The current queue sheet and lyrics view use different layouts and back gestures. Port the shared transition with those destinations so back, drag cancellation and track changes can be checked together. The original library and detail pages, navigation, player menus and alternate styles also remain open. Compare actual Android and iOS screenshots and wide desktop captures at the same playback state before calling a screen visually matched.

## Feature mapping

| Area | Current integration | Work left for parity |
| --- | --- | --- |
| Themes and typography | Mochy's neutral palette, dark defaults and text spacing feed the app theme. The mobile Flagship player bundles the original Inter fonts. | Using the original font policy throughout the mobile app, theme editor and the other appearance options. |
| Player chrome | The mobile Flagship uses original transport, seek, collapse, and action controls, artwork double-tap LikeBurst, and the Flagship landscape layout (blurred artwork backdrop, rounded cover, Song Info / Lyrics / Queue right panel, cover action buttons). Portrait includes the volume track and output, lyrics and queue pills. The compact desktop and default expressive mobile mini-players use the play glyph. | Original mobile artwork transition, card deck, page morphs, alternate vinyl/turntable and iPod styles, and overflow menu styling. |
| Home and library | Desktop and mobile Home render Mochy's animated hero carousel with ambient cover glow, 3D press-tilt, and offline playable fallback on desktop. Metrolist continues to provide its own shared feeds, discovery, search and library. | Remaining carousel states, specialist shelves, Jump Back In layout, library cards, playlist and album detail, and recap presentation. |
| Profile and onboarding | Local Mochy profile ID card with drag tilt, Obsidian/Brushed Silver finishes, platform photo picker (Android, Desktop, iOS), grapheme-safe display name clamping, and welcome celebration card. | Syncing visual theme presets from the onboarding flow. |
| Bedside | Full-screen OLED bedside clock with three Mochy faces (Big with FlipDigits, Stacked, Analog with 60-tick dial and spring hands), swipe-up Now Playing artwork/transport view, warm salmon-orange night-tint rest dimming, and periodic OLED burn-in drift. | Bedside tiles layout and Android lock-screen presentation flags. |
| Library transfer | Import from another app recognizes Mochy backups and merges likes, playlists, play history, playlist descriptions, edited song titles and artist text, and lyric offsets. Mochy module enablement reloads cleanly on settings backup restore. | Mochy-specific account sessions, local files, downloads, pins, artwork files, lyric text and other fields outside the shared import model. |
| Audio and integrations | Keep the base player's queue, video and EQ and the base account, import and scrobbling flows. Play/pause transport fade settles cleanly before seek on Android, Desktop, and iOS. In-app updaters point to `NatuneGroup/Mochy` releases. | Match Mochy Audio Lab, smart CrossMix and stage DSP, video and canvas behavior, lyric providers, sharing and third-party integrations against the existing implementations. |

## Mochy modules

Enabled sources can supply audio for ordinary music tracks. Downloads, local files, video, podcasts, uploads, live streams, Cast, and synchronized playback use their existing paths. If a module cannot resolve or prepare a track, the player falls back to its standard source. Android and desktop accept progressive, HLS and DASH sources. iOS accepts progressive and HLS and skips DASH. Desktop inline DASH manifests need absolute segment URLs or an absolute base URL.

A web module's `look` can set `player` to `flagship`, `modern`, `vintage` or `classic`; `mini` to `expressive`, `modern` or `vintage`; `bar` to `standard`, `compact` or `floating`; and `font` to `inter` or `system`. These overrides apply where the host has the corresponding layout. Disabling the module restores the saved preferences. These keys select existing layouts and fonts. Mochy's custom skin layouts and packaged assets still need their own renderer.

Enabled lyrics modules can supply missing lyrics or improve timing after the built-in providers run. Enabled metadata modules can supply title, artwork and unlinked artist or album information for the current track. The metadata applies to the player display and preserves local edits, linked artists and albums, and library data.

## Verification before publishing

Use GitHub Actions for Android, desktop and iOS compilation and the shared desktop test suite. Check the player on a phone and in a narrow and wide desktop window. Resize while queue or lyrics is open, switch theme, toggle playback from the dock and clock, and verify keyboard and screen-reader access. Compare Android to the Mochy source screenshots. The coverage for backup import includes a fixture with duplicate playlist entries, song edits, two imports of the same file and edits already present in the destination.

For the Flagship bar, open and close the queue and lyrics, drag volume past each end, change Android media volume with the hardware keys, cancel a drag by collapsing the player, and repeat with light artwork. Confirm that the output pill identifies a connected cast receiver and opens device selection. On iOS, check that dragging changes audible playback and that the level still reads correctly after reopening the player. Verify Home headings with long labels and RTL, and check that linked headings, Play All and Shuffle still perform their actions.

For Home, compare portrait, tablet and landscape framing. With a song paused at a known position, tap the current song's pill and verify it resumes there with the existing queue. Confirm that album, artist and playlist cards open their pages. Open another screen or expand the player while a card is visible and check that the carousel pauses.

Check the mobile shelves with one, two and more than three songs. Swipe between columns, open a track's menu with both touch paths, and repeat with large text and reduced motion. Open albums from cover cards and test returning to the same scroll position.
