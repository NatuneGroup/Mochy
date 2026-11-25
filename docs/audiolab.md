# Audio Lab

Source: Mochy Music (realidkroo, GPL-3.0), `app/src/main/kotlin/com/mochyy/mochymusic/eq/`. The reference chain is built in `playback/MusicService.kt:1407-1411`: Stage, Spatial, Reverb, one more processor, then EightD.

## Effects

| Effect | Reference files | Native | What it does | Settings |
| --- | --- | --- | --- | --- |
| Reverb | `eq/audio/ReverbAudioProcessor.kt`, `eq/ReverbSettings.kt` | No, pure Kotlin | Freeverb-style room: 8 damped combs into 4 allpasses per side, right side detuned for width. Mono pre-delay and low cut on the send keep bass dry. Soft-knee output. | enabled, mix, roomSize, damping, wet, dry, width, preDelayMs (0-150), lowCutHz; 8 presets |
| 8D | `eq/audio/EightDAudioProcessor.kt` | No, pure Kotlin | Slow equal-power pan around the head (sine LFO). Stereo only. | enabled, rotationSpeedHz (0.05-0.5), depth (0-1). The reference `reverbLevel` field is not used by its DSP and is not ported. |
| Stage (3D room) | `eq/audio/StageAudioProcessor.kt`, `eq/stage/*.kt` | **Yes**: C++ in `app/src/main/cpp/stage/` (engine, dsp, hrtf, splitter, tail, JNI), CMake, `assets/stage_hrtf.bin`, JNI library `mochystage` | Room simulation with HRTF for headphones or virtual speakers for speakers. | StageParams, StagePresets, StageSettings |
| Spatial | `eq/audio/SpatialAudioProcessor.kt` | No, pure Kotlin | Crossfeed for headphones, or virtual speakers, plus 5.1/7.1 rendered to two ears. | SpatialParams (mode, cutoffHz, feedDb, speakerAngle, amount) |

## Android plan

Media3 `BaseAudioProcessor` subclasses in `androidApp/src/main/kotlin/com/metrolist/android/playback/`, following `EqualizerAudioProcessor`.

- Reverb and 8D: ported in Part 1. Both return `isActive() == false` while disabled and are built with the sink in `SilenceSkippingSupport.kt`, after the equalizer. Their settings types live in `shared/src/commonMain/kotlin/com/metrolist/shared/audiolab/`. Preferences are not wired yet.
- Spatial: pure Kotlin, next after Part 1.
- Stage: needs the NDK build (CMake, JNI library, HRTF asset). A separate wave.

Note: Media3 reads `isActive()` when the sink configures, so a change to enabled applies on the next configure (next track or format change), not mid-buffer.

## Desktop plan

Engine: `shared/src/desktopMain/kotlin/com/metrolist/shared/playback/ffmpeg/AudioRenderer.kt`. The equalizer runs at line 129 (`val equalized = equalize(data, boostDb)`) and the silence skipper at line 131. An effect hook fits between those two lines, on the PCM from `equalized`. Not wired in Part 1.

## iOS plan

Engine: `MTAudioProcessingTap` in `shared/src/iosMain/kotlin/com/metrolist/shared/playback/IosAudioProcessingTap.kt`. The tap reads `EqualizerProfiles.configuration` (line 66), and `processIosAudioTap` (line 256) processes the buffers. An effect can run after the equalizer there. HLS fallback bypasses the tap, so effects are unavailable on HLS streams (see `docs/audio-processing.md`). Not wired in Part 1.

## Not done in Part 1

- Stage and Spatial ports.
- Desktop and iOS hooks.
- Preferences, the Audio Lab UI, and the `update()` calls from the player.
- Unit tests: `shared/src/commonTest` is kept small by policy (see `CLAUDE.md`), and the preset and clamping values here are constants.
