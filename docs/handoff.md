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

Snapshot updated 2026-11-25.

- PR #1 is open and not a draft. `AI usage:` is disclosed in the PR body.
- Last full CI on `3d2a162e` passed: Select checks and repository checks, Android / APK,
  Desktop / Linux x64 and iOS / Validate iOS.
- The signed release from `eb2e7514` (`build-android.yml`, `variant=release`, run
  45550453298) produced `Mochy.apk` at 16,935,941 bytes. It has v2 and v3 signatures,
  and `apksigner` printed `CN=Mochy (NatuneGroup), O=NatuneGroup`.
- Release signing secrets are set in `NatuneGroup/Mochy` (2026-11-25): `KEYSTORE_PATH`
  (base64 PKCS12), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Certificate
  `CN=Mochy (NatuneGroup), O=NatuneGroup`, SHA-256
  `B8:8C:3C:FE:9C:4A:F0:A0:D0:9F:70:30:E8:A7:A4:DD:7B:43:55:ED:86:07:3D:A3:03:0E:59:D5:11:13:1F:95`.
  The keystore and its password are kept offline by the operator, never in the repo.
- Formatting without a local Gradle run: push a commit whose message contains
  `[ktlint-format]`. `.github/workflows/ktlint-format.yml` formats on Blacksmith and pushes
  `style: apply ktlintFormat`. CI started by that bot push waits for approval
  (`gh api -X POST repos/NatuneGroup/Mochy/actions/runs/<id>/approve`). If nothing needed
  formatting, rerun the PR's CI run instead.
- Compose resource strings accept only `%1$s` and `%1$d` placeholders, so do not use `%%`
  or `%.2f`. Each `Res.string.<key>` needs its own import unless the file has the wildcard
  import. `python3 scripts/check_translation_mechanics.py` and
  `python3 scripts/check_compose_resource_escapes.py` catch the first two locally.
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
| Wave 1: navigation and screens | `7456612c`, `0afa9be1` | Mochy nav pill, library, detail, search and settings styling. |
| Wave 2: player styles | `9f520afa`, `8229f32b` | Turntable/vinyl style and iPod click-wheel style. The wheel has screen-reader actions, and a pending wheel seek is tied to the queue item. |
| Review round (PR #1) | `e8fa57e8`, `80c9b777`, `c8bf80a7`, `4f3db510` | Desktop data under `mochy/`, iOS bundle ID `com.natune.mochy`, profile name clamp, offline desktop hero fallback. |
| Wave 3: Audio Lab | `a4bf6592`…`605985b4` | Spatial (crossfeed/virtual speakers), Freeverb-style reverb with presets, and 8D. Shared DSP cores are in `shared/.../audiolab/dsp` and run on Android (Media3 wrappers), desktop (`AudioRenderer`) and iOS (audio tap; HLS bypasses it). The settings page is reached from Playback. See [audiolab.md](audiolab.md). |
| Wave 4: share and profile | `906f387a`, `ff0cb673`, `03394cca`, `14011c10` | ID card back face with tap flip. The song share card (cover/wide) is in the player menu on Android and iOS. Recap is the existing Wrapped feature, so it was not duplicated. |
| Audio Lab Stage | `a83da2e0`…`674080bf` | Native `stageEngine` module, built like `opusDecoder` and ordered after it so the two NDK installs don't run at once. Stage settings, presets and card are included. |
| Wiring and share | `c733578f`…`b50a283c` | ID card back stats come from listening history. iOS share files are named `mochy-statistics-` and `mochy-song-`. The desktop player menu has a song card option. The lyrics card cover uses the reference rounding. |
| Flagship cover looks | `7b37ed23`…`9ac6a6b3` | `PlayerCoverStyle` (Square, Full bleed, Disc) applies to portrait, landscape and desktop fullscreen. Landscape hides the seek bar and transport after 3.5 s on the lyrics page. |
| Desktop Mochy UI | `556e0d28`…`3d2a162e` | Sidebar with the Mochy pill, hover and font. The bottom bar uses the Flagship transport and seek track. The wide fullscreen player uses the Flagship layout. Home hero cards tilt on hover. |
| CI formatting | `edaaf448` | `[ktlint-format]` workflow (see Current state). |

### Android release details

- `androidApp/build.gradle.kts`: `applicationId = "com.natune.mochy"`,
  debug suffix `.debug`, label `Mochy` / `Mochy Debug`.
- Launcher icon: Mochy play-triangle mark in `ic_launcher_foreground.xml` and
  `ic_launcher_monochrome.xml`, background `#FF1A1A1A`.
- Signing order: `KEYSTORE_*` secrets (the real Mochy key), then the published
  `androidApp/keystore/mochy-test.p12` (only for builds without secrets, such as forks),
  then the runner debug keystore. An APK signed with the test key cannot update to a
  secret-signed one. See `androidApp/keystore/README.md`.
- `scripts/collect_android_apks.py` emits `Mochy.apk`, `Mochy-izzy.apk` and
  `mochy-android-<version>-debug.apk`.
- `reusable-android-build.yml` verifies every collected APK with
  `apksigner verify --verbose --print-certs` and only requires Last.fm
  secrets when `require-signing` is true.

## Remaining work

- **Release channels (branch `ci/natune-release-flow`, not pushed).** Nightlies come from `dev` and stable releases from `main`, numbered by `.github/scripts/next-version.py` (Natune's model). The in-app "Nightly builds" switch already exists on Android and desktop. Ten `.github/tests` checks compare against the old Metrolist workflow text, and they have to be removed or rewritten before this lands; that needs the operator's OK.
- **Desktop polish.** The fullscreen Flagship has no like, share or cast buttons. The bottom-bar transport has no hover state. Shelf cards keep the default press feedback.
- **Lyrics share card.** Only the cover rounding matches the reference. Its "Cinematic" layout (`ShareCanvas.kt`) is not ported.

Waves 1 to 4 have landed (see Completed work). Still open:

- **Stage follow-ups.** The Stage room (native `stageEngine` module, `libmochystage.so`, KEMAR HRTF asset) runs on Android in stereo PCM16 with the headphone model. Not yet ported: the room and scene drawing, editing objects one by one, speaker-output detection and surround downmix. Desktop and iOS show the card disabled.
- **Flagship landscape extras.** The layout already matches the reference: cover on the left, with the info, lyrics and queue panel on the right. Still missing are the reference's cover looks (full-bleed, disc, record, TV), which need a cover-style setting first, plus the lyrics band, frost, reflection and auto-hiding control deck.
- **Share Studio extras.** Video/karaoke export (`share/song/export/**`, Android GL) is not
  ported. The lyrics share card keeps the Metrolist look.
- **Audio Lab module switch.** `MochyModuleRegistry` has no effect-module kind, so disabling a
  module cannot reset the effects.
- **Device checks.** Nothing here was tested on a device: Audio Lab sound and toggling
  mid-song, TalkBack/VoiceOver on the click wheel, side-by-side desktop and iOS installs.

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

- `com.natune.mochy` is its own app. It installs next to the early
  `com.metrolist.music` preview, Metrolist and original Mochy (`com.mochyy.mochymusic`),
  so testers can keep the old app and compare. Only builds signed with the same key update
  each other in place: test-key builds made before 2026-11-25 must be reinstalled once to
  move to the real key. Export a backup from the old app and check the import in Mochy
  before removing anything; data is not migrated between apps automatically.
- iOS uses bundle ID `com.natune.mochy` and display name Mochy, with the background task
  `com.natune.mochy.automatic-backup`. The `metrolist` and `metrolist-auth` URL schemes
  are unchanged because the sign-in redirects registered with providers use them; with both
  apps installed, iOS may open either one for those links.
- Desktop keeps its settings, library, lock, backups, caches and logs under `mochy/` in
  the XDG directories (`Mochy` for WebView data on Windows and macOS), so it runs beside
  Metrolist. It no longer reads or clears Metrolist's java.util.prefs nodes. Importing a
  Metrolist library is a manual backup and restore.
- Intent actions remain `com.metrolist.music.action.*` on purpose. Shortcuts and
  internal receivers use them, and they are not package IDs.
- Without Last.fm secrets the release APK ships empty keys. Those features
  degrade; set `require-signing: true` for a store-quality build with secrets.
- The R8 mapping artifact is large (about 359 MB uncompressed) and is now named
  `Mochy-KMP-mapping.txt` alongside the APK assets.
- `com.metrolist` remains the Kotlin package namespace. Renaming packages would be a
  large, risk-only change; the app ID and label are what users see.

## Next actions

1. Install `Mochy.apk` from the latest release run on a phone. Check Audio Lab (Spatial,
   Reverb, 8D), the song share card and the ID card flip.
2. On a phone, check that Stage loads (the card is enabled) and test a preset with headphones.
3. Flagship cover looks and lyrics band (needs a cover-style setting).
4. Answer any new review comments on PR #1.

Do not bump `appVersion`, rewrite history, change Blacksmith runners, add paywalls,
or remove Mochy/Metrolist/ViviMusic credit.
