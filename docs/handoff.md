# Mochy multiplatform handoff

Snapshot: 2026-11-24. Branch `feat/mochy-multiplatform`, open PR
[#1](https://github.com/NatuneGroup/Mochy/pull/1) into `main`. This file records what
is built, what is left, and where the reference code lives.

Read first: [mochy-port.md](mochy-port.md), [../CLAUDE.md](../CLAUDE.md) and
[../AGENTS.md](../AGENTS.md).

## Repositories and environment

- Work repo: `github.com/NatuneGroup/Mochy`, local `/home/factory-user/repos/Mochy`.
- Visual and feature reference: `github.com/realidkroo/mochy-music` (GPL-3.0), local
  `/home/factory-user/repos/Mochy-Music`. Mochy itself descends from ViviMusic and
  Metrolist; keep those credits.
- Base: Metrolist KMP at `af3d05339d74b342c0a49af308eb81c33d380deb`.
- Local machine has no JDK or Android SDK. Build, ktlint and packaging run on GitHub
  Actions. Python tooling tests run locally.

## Current state

- PR #1 is open and not a draft. `AI usage:` is disclosed in the PR body.
- Last full run on `0bdd6d9a` passed: Android / APK, Desktop / Linux x64,
  iOS / Validate iOS, Select checks and repository checks, and disclosure.
- Android artifact from that run: `Mochy.apk`, 16,809,276 bytes (16.8 MB),
  `com.natune.mochy`, version 14.0.1, v1 + v2 + v3 signatures, 3 dex files,
  18 native library entries. The previous debug APK was 48 MB with 23 dex files.
- No GitHub Actions secrets are configured in `NatuneGroup/Mochy`
  (`total_count: 0`). CI therefore builds release with the published test key added
  in this handoff commit.
- CI run history: `gh run list --repo NatuneGroup/Mochy --branch feat/mochy-multiplatform`.

## Completed work

| Area | Commits | Notes |
| --- | --- | --- |
| Flagship player and animation cards | `0af797f0`, `bbb63d5c` | Portrait and landscape Flagship layouts, transport, seek track, shade, handle, like burst, Inter font. |
| Play/pause transport fades | `ebd6530c`, `d18baccf` | Smoothstep fades on Android, Desktop and iOS; fades settle before seek. |
| Mochy module system | `6220ac18`, `2b3e3ccf`, `be68724e` | `MochyModule`, `SourceModule`, web module manifests, catalog, registry; playback and lyrics/metadata wiring. Uses Mochy's module system, not Natune's `.arch` addons. |
| Home carousel and shelves | `131e394c`, `4a6fd996`, `5bae3070` | Hero carousel with ambient glow, 3D press tilt, peek cards, dots, resume/recommendation/history actions, three-row snapping song shelves, cover shelves, offline desktop fallback. |
| Profile ID card | `27fe9269`, `d18baccf` | Draggable tilt, Obsidian/Brushed Silver finishes, platform photo pickers, grapheme-safe name clamping, welcome card. |
| Updater | `d18baccf` | In-app updater points at `NatuneGroup/Mochy` releases on Android and Desktop. |
| Bedside Clock | `50960cf0` | Three faces (Big FlipDigits, Stacked, Analog), swipe-up Now Playing, night tint, burn-in drift, persisted settings, all platforms. |
| Android release packaging | `0bdd6d9a`, this commit | Mochy app ID/label/icon, R8 + resource shrinking, `Mochy.apk` collection, `apksigner verify` on every APK, persistent published test key. |

### Android release details

- `androidApp/build.gradle.kts`: `applicationId = "com.natune.mochy"`,
  debug suffix `.debug`, label `Mochy` / `Mochy Debug`.
- Launcher icon: Mochy play-triangle mark in `ic_launcher_foreground.xml` and
  `ic_launcher_monochrome.xml`, background `#FF1A1A1A`.
- Signing order: `KEYSTORE_*` secrets, then `androidApp/keystore/mochy-test.p12`
  (`CN=Mochy Test Build`, password `mochytest`, alias `mochy-test`), then the
  runner debug keystore. The published test key keeps signatures stable across
  ephemeral CI runners. See `androidApp/keystore/README.md`.
- `scripts/collect_android_apks.py` emits `Mochy.apk`, `Mochy-izzy.apk` and
  `mochy-android-<version>-debug.apk`.
- `reusable-android-build.yml` verifies every collected APK with
  `apksigner verify --verbose --print-certs` and only requires Last.fm/Lyprio
  secrets when `require-signing` is true.

## Remaining waves

Wave 1 is the largest. Each wave should land as its own reviewable change on the
same branch unless maintainers ask for separate PRs.

### Wave 1: UI shell and screens

Reference:

- `Mochy-Music/app/src/main/kotlin/com/mochyy/mochymusic/ui/component/NavPill.kt`
- `.../ui/component/AppNavigation.kt`
- `.../ui/screens/library/**`
- `.../ui/screens/search/**`
- `.../ui/screens/AlbumScreen.kt`, `.../ui/screens/artist/**`, `.../ui/screens/playlist/**`
- `.../ui/component/detail/**`

Targets:

- `shared/src/commonMain/kotlin/com/metrolist/shared/ui/navigation/FloatingNavigationBar.kt`
  currently draws a static capsule. Port Mochy's `NavPillState`: two `Animatable`
  edges with lead/trail springs, lift on touch, horizontal drag across tabs, tap
  settle fallback, and fade when no tab is lit.
- `AdaptiveNavigationScaffold.kt` passes items and heights; keep its desktop rail and
  all platform entry points working. Android legacy navigation is
  `androidApp/src/main/kotlin/com/metrolist/android/ui/Navigation.kt`; KMP mobile
  host is `shared/src/commonMain/kotlin/com/metrolist/shared/ui/mobile/MobileApp.kt`.
- Library screens exist as `LibraryContent.kt`; split or restyle against Mochy's
  `LibraryHomeScreen`, `LibraryTabs`, `LibraryShelfCards`, `LibraryGridScreen`,
  `LibraryEntityListScreen`, `LibraryImport*`.
- Detail screens: `AlbumContent.kt`, `ArtistScreen.kt`, `PlaylistContent.kt`,
  `StoredPlaylistContent.kt`; Mochy's shared pieces are in
  `ui/component/detail/` (collapsing top bar, cover background, action row,
  track row, side layout).
- Search: `SearchScreen.kt` / `SearchContent.kt`; Mochy reference
  `ui/screens/search/SearchDesign.kt`, `SearchSidePane.kt`, `RecentSearches.kt`.
- Settings: reuse existing settings pages, apply Mochy type/spacing and section
  styling only; do not rename preference keys.

Platform notes: `NavPill` has no Android imports and ports into `commonMain`.
`RemoteFocus` and haptics are Android-only and must stay behind existing platform
abstractions. Mochy uses Coil painters for SVG tab icons; Metrolist has vector icons,
so select the nearest `MetrolistIcons` and keep tab identity stable.

### Wave 2: Vinyl, turntable and iPod player styles

Reference:

- `.../ui/player/customize/TurntableArm.kt`, `TurntableArt.kt`
- `.../ui/player/TurntableControls.kt`
- `.../ui/player/ipod/*` (`IpodClickWheel.kt`, `IpodScreen.kt`, `IpodNowPlaying.kt`,
  `IpodCoverFlow.kt`, `IpodMenu.kt`, `IpodGeometry.kt`, `IpodLook.kt`)

Targets:

- `shared/src/commonMain/kotlin/com/metrolist/shared/ui/player/mochy/` holds the
  ported Flagship and Bedside work; add `VinylDisc`, `TurntableArm`, and `IpodPlayer`.
- Wire style selection through settings and the existing module `look.player` mapping
  (`flagship`, `modern`, `vintage`, `classic`) where it already exists.
- Tonearm drag maps angle to seek position with a commit on release; disc rotation
  follows playback position and pauses with the player. Reuse the existing
  `FlagshipSeekTrack` interaction rules for tap, drag and cancellation.
- iPod Click Wheel needs circular gesture math, detents and haptics; keep seek and
  volume on the same player APIs, not a second player.

### Wave 3: Audio Lab

Reference:

- `.../ui/screens/audiolab/**` including `AudioLabScreen.kt`, `ReverbCard.kt`,
  `stage/Stage*`
- `.../viewmodels/AudioLabViewModel.kt`, `.../di/AudioLabEntryPoint.kt`
- `.../eq/AudioLabStore.kt`, `ReverbSettings.kt`
- `.../eq/audio/ReverbAudioProcessor.kt`, `StageAudioProcessor.kt`
- `.../eq/stage/StageNative.kt`, `StageOutput.kt`, `StageSettings.kt`, `StagePresets.kt`

Targets:

- Android audio processing lives in
  `androidApp/src/main/kotlin/com/metrolist/android/playback/`
  (`EqualizerAudioProcessor.kt`, `NormalizationAudioProcessor.kt`,
  `ExoPlaybackPlayer.kt`). Add 8D/reverb/stage processors to that chain and expose
  enable/disable through `MochyModuleRegistry` so disabled modules restore defaults.
- Shared settings go through `PreferenceKeys.kt` and `Settings.shared`.
- Stage room UI port belongs in `shared/.../ui/mochy/` or a new `ui/audiolab/`
  package. `StageNative.kt` is Android JNI; iOS and desktop need an expect/actual
  fallback or a shared pan/delay implementation. Audit the DSP path before coding;
  do not pretend parity where a platform cannot run the native stage engine.
- CrossMix transitions must reuse the player queue and transport, not add a second
  queue (rejected feature).

### Wave 4: Share Studio and Recap Story

Reference:

- `.../ui/player/share/**` (`ShareKit.kt`, `ShareCanvas.kt`, `SharePalette.kt`,
  `ShareChoreography.kt`, `LyricsShareSession.kt`, `song/SongShare*.kt`,
  `song/export/**`)
- `.../ui/screens/recap/**` (`RecapStory.kt`, `RecapStoryPages.kt`, `RecapData.kt`,
  `RecapVisuals.kt`, `IdCardBack.kt`, `RecapSnippetPlayer.kt`)
- `.../ui/screens/setup/onboarding/ProfileIdCard.kt`

Targets:

- Existing Metrolist share code: `shared/.../ui/components/lyrics/LyricsShareDialog.kt`
  and player share entry points in `PlayerViewModel.kt`.
- Share image/video export is platform-specific: Android MediaStore + MediaCodec/GL,
  iOS AVFoundation/`UIActivityViewController`, desktop Skia file save. Define an
  expect/actual export boundary and test each host. `share/song/export/ShareGlEffects.kt`
  is Android GL and cannot compile in `commonMain`.
- Recap data needs an event source. Metrolist has statistics/wrapped code; extend it
  instead of adding a parallel history table.
- `IdCardBack` and the existing `MochyProfileIdCard` should share one geometry and
  finish model.

## Reuse instead of duplicating

- Module system: `shared/.../modules/MochyModuleApi.kt`,
  `MochyModuleRegistry.kt`, `MochyPlayback.kt`, `MochyLyrics.kt`, `MochyMetadata.kt`.
- HTTP and crypto: `createHttpClient()`, `DeviceCrypto.sha256`, `md5Hex`.
- Playlists and covers: `readPlaylistCover`, `pickIosPlaylistCover`,
  `storeIosPlaylistCover`.
- Player: `MochyTransportFader`, Flagship components, existing queue/lyrics/video
  destinations.
- Home: `MochyHeroCarousel`, `MochyHomeShelves`, `MochyMotion`,
  `MochyRevealOnAppear`.
- Profile: `MochyProfileIdCard`, platform photo pickers.
- Onboarding, equalizer, statistics, backup/import already exist; extend them.

## Verification

Android release APK:

```bash
gh workflow run build-android.yml --repo NatuneGroup/Mochy \
  --ref feat/mochy-multiplatform -f variant=release
gh run list --repo NatuneGroup/Mochy --branch feat/mochy-multiplatform --limit 3
gh run download <run-id> --repo NatuneGroup/Mochy --dir /tmp/mochy-apk
```

The `apksigner` step must print `CN=Mochy Test Build` with certificate SHA-256
`0F:32:3E:19:D9:B2:D8:37:B9:7E:E7:D4:D5:F8:3B:7A:7B:9F:F0:38:8A:F6:9D:40:26:6C:5E:D4:74:06:FE:49`
(or the production certificate when `KEYSTORE_*` secrets exist). Run the workflow
twice and compare the certificate SHA-256 to confirm stable update signatures.

Desktop and iOS:

```bash
gh workflow run build-desktop.yml --repo NatuneGroup/Mochy --ref feat/mochy-multiplatform
gh workflow run build-ios.yml --repo NatuneGroup/Mochy --ref feat/mochy-multiplatform
```

Local checks:

```bash
cd scripts && python3 -m unittest test_collect_android_apks -v
cd .. && python3 -m unittest discover -s .github/tests -v
git diff --check
```

The full `scripts` discovery suite needs a JDK and fails on this machine; the
workflow tests and Android collector test do not.

## Known caveats

- The app ID changed from `com.metrolist.music` to `com.natune.mochy` and the signing
  key changed, so an APK installed from an earlier build must be uninstalled once.
  Data from the old package is not migrated automatically; use the app's backup and
  restore.
- Intent actions remain `com.metrolist.music.action.*` on purpose. Shortcuts and
  internal receivers use them, and they are not package IDs.
- Without Last.fm/Lyprio secrets the release APK ships empty keys. Those features
  degrade; set `require-signing: true` for a store-quality build with secrets.
- The R8 mapping artifact is large (about 359 MB uncompressed) and is now named
  `Mochy-KMP-mapping.txt` alongside the APK assets.
- `com.metrolist` remains the Kotlin package namespace. Renaming packages would be a
  large, risk-only change; the app ID and label are what users see.

## Next actions

1. Push this handoff plus the test keystore and confirm the release APK verifies
   with `CN=Mochy Test Build` on two consecutive runs.
2. Port `NavPill` into `FloatingNavigationBar.kt` (Wave 1, highest visible impact).
3. Restyle Library, detail and Search screens against the Mochy reference; keep
   desktop rail behavior.
4. Wave 2 vinyl/turntable, then iPod.
5. Wave 3 Audio Lab with a per-platform DSP audit before implementation.
6. Wave 4 Share Studio and Recap.

Do not bump `appVersion`, rewrite history, change Blacksmith runners, add paywalls,
or remove Mochy/Metrolist/ViviMusic credit.
