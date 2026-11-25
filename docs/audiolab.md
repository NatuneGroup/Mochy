# Audio Lab

Source: Mochy Music (realidkroo, GPL-3.0), `app/src/main/kotlin/com/mochyy/mochymusic/eq/`. The reference chain is built in `playback/MusicService.kt:1407-1411`: Stage, Spatial, Reverb, one more processor, then EightD.

## Effects

| Effect | Reference files | Native | What it does | Settings |
| --- | --- | --- | --- | --- |
| Reverb | `eq/audio/ReverbAudioProcessor.kt`, `eq/ReverbSettings.kt` | No, pure Kotlin | Freeverb-style room: 8 damped combs into 4 allpasses per side, right side detuned for width. Mono pre-delay and low cut on the send keep bass dry. Soft-knee output. | enabled, mix, roomSize, damping, wet, dry, width, preDelayMs (0-150), lowCutHz; 8 presets |
| 8D | `eq/audio/EightDAudioProcessor.kt` | No, pure Kotlin | Slow equal-power pan around the head (sine LFO). Stereo only. | enabled, rotationSpeedHz (0.05-0.5), depth (0-1). The reference `reverbLevel` field is not used by its DSP and is not ported. |
| Stage (3D room) | `eq/audio/StageAudioProcessor.kt`, `eq/stage/*.kt` | **Yes**: C++ in `app/src/main/cpp/stage/` (engine, dsp, hrtf, splitter, tail, JNI), CMake, `assets/stage_hrtf.bin`, JNI library `mochystage` | Room simulation with HRTF for headphones or virtual speakers for speakers. | StageParams, StagePresets, StageSettings |
| Spatial | `eq/audio/SpatialAudioProcessor.kt` | No, pure Kotlin | Crossfeed for headphones, or two virtual speakers. Stereo PCM16 only on Android; the 5.1/7.1 downmix and mono listening are not ported. | enabled, mode (crossfeed or speakers), cutoffHz (300-1500), feedDb (1-15), speakerAngle (10-90), amount (0-1) |

## Android plan

Media3 `BaseAudioProcessor` subclasses in `androidApp/src/main/kotlin/com/metrolist/android/playback/`, following `EqualizerAudioProcessor`.

- Spatial, Reverb and 8D: done. All three are always in the chain, after the equalizer, built in `SilenceSkippingSupport.kt`. Spatial sits before Reverb, as in the reference. While disabled they pass audio through untouched; enabling or disabling fades them in and out, so changes apply mid-song without reconfiguring the sink. Types live in `shared/src/commonMain/kotlin/com/metrolist/shared/audiolab/`, saved values in `Settings.shared` (`reverb*` and `eightD*` keys), and `AudioLabSettings.kt` turns them into flows that each player's `SilenceSkippingSupport` applies.
- Spatial: done (`SpatialAudioProcessor.kt`, settings `spatial*`).
- DSP: `shared/src/commonMain/kotlin/com/metrolist/shared/audiolab/dsp/` holds `SpatialCore`, `ReverbCore`, `EightDCore` and the shared soft knee (`SoftKnee.kt`). The Android processors are Media3 wrappers around them (`Pcm16CoreAudioProcessor.kt`). Desktop and iOS run the same cores through `AudioLabChain` in commonMain, in the same order. Spatial and reverb go through the soft knee at the output edge, 8D is clipped to the sample range.
- Stage: needs the NDK build (CMake, JNI library, HRTF asset). A separate wave.

Settings page: the Audio Lab row sits next to the equalizer in the sound settings. The cards work on Android, desktop and iOS. On iOS a note says effects do not apply to HLS streams.

## Desktop plan

Engine: `shared/src/desktopMain/kotlin/com/metrolist/shared/playback/ffmpeg/AudioRenderer.kt`. The equalizer runs at line 129 (`val equalized = equalize(data, boostDb)`) and the silence skipper at line 131. Hook: `AudioLabChain.processPcm16` runs on `equalized` between those two lines. The renderer owns the chain and its settings scope, `reset()` runs with the equalizer on flush, and `close()` cancels the scope.

## iOS plan

Engine: `MTAudioProcessingTap` in `shared/src/iosMain/kotlin/com/metrolist/shared/playback/IosAudioProcessingTap.kt`. The tap reads `EqualizerProfiles.configuration` (line 66), and `processIosAudioTap` (line 256) processes the buffers. Hook: `IosAudioTapState.process` runs `AudioLabChain.process` on the float buffer after the equalizer and the spectrum meter. `prepare` configures the chain, `unprepare` stops it, and settings reach the cores from the tap scope. HLS fallback bypasses the tap, so effects are unavailable on HLS streams (see `docs/audio-processing.md`).

## Not done yet

- Stage port.
- A Mochy module entry for Audio Lab. The module registry only models source, lyrics, look and metadata modules, with no effect-module pattern, so turning a module off cannot reset the effects yet.
- Unit tests: `shared/src/commonTest` is kept small by policy (see `CLAUDE.md`), and the preset and clamping values here are constants.
