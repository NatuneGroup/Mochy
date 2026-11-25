# Audio Lab

Source: Mochy Music (realidkroo, GPL-3.0), `app/src/main/kotlin/com/mochyy/mochymusic/eq/`. The reference chain is built in `playback/MusicService.kt:1407-1411`: Stage, Spatial, Reverb, one more processor, then EightD.

## Effects

| Effect | Reference files | Native | What it does | Settings |
| --- | --- | --- | --- | --- |
| Reverb | `eq/audio/ReverbAudioProcessor.kt`, `eq/ReverbSettings.kt` | No, pure Kotlin | Freeverb-style room: 8 damped combs into 4 allpasses per side, right side detuned for width. Mono pre-delay and low cut on the send keep bass dry. Soft-knee output. | enabled, mix, roomSize, damping, wet, dry, width, preDelayMs (0-150), lowCutHz; 8 presets |
| 8D | `eq/audio/EightDAudioProcessor.kt` | No, pure Kotlin | Slow equal-power pan around the head (sine LFO). Stereo only. | enabled, rotationSpeedHz (0.05-0.5), depth (0-1). The reference `reverbLevel` field is not used by its DSP and is not ported. |
| Stage (3D room) | `eq/audio/StageAudioProcessor.kt`, `eq/stage/*.kt` | **Yes**: `stageEngine/src/main/cpp/stage/` (engine, dsp, hrtf, splitter, tail, JNI), CMake, `androidApp/src/main/assets/stage_hrtf.bin`, JNI library `mochystage` | 3D room with HRTF placement of each part of the song, echoes off six walls and a late tail. Headphone model only on Android. | enabled, presetId (6 presets), roomSize, walls (set by the preset), roomAmount, levelDb |
| Spatial | `eq/audio/SpatialAudioProcessor.kt` | No, pure Kotlin | Crossfeed for headphones, or two virtual speakers. Stereo PCM16 only on Android; the 5.1/7.1 downmix and mono listening are not ported. | enabled, mode (crossfeed or speakers), cutoffHz (300-1500), feedDb (1-15), speakerAngle (10-90), amount (0-1) |

## Android plan

Media3 `BaseAudioProcessor` subclasses in `androidApp/src/main/kotlin/com/metrolist/android/playback/`, following `EqualizerAudioProcessor`.

- Stage, Spatial, Reverb and 8D: done. All four are always in the chain, after the equalizer, built in `SilenceSkippingSupport.kt`. Stage sits first among the effects, before Spatial, as in the reference. While disabled they pass audio through untouched; enabling or disabling fades them in and out, so changes apply mid-song without reconfiguring the sink. Types live in `shared/src/commonMain/kotlin/com/metrolist/shared/audiolab/`, saved values in `Settings.shared` (`reverb*` and `eightD*` keys), and `AudioLabSettings.kt` turns them into flows that each player's `SilenceSkippingSupport` applies.
- Spatial: done (`SpatialAudioProcessor.kt`, settings `spatial*`).
- DSP: `shared/src/commonMain/kotlin/com/metrolist/shared/audiolab/dsp/` holds `SpatialCore`, `ReverbCore`, `EightDCore` and the shared soft knee (`SoftKnee.kt`). The Android processors are Media3 wrappers around them (`Pcm16CoreAudioProcessor.kt`). Desktop and iOS run the same cores through `AudioLabChain` in commonMain, in the same order. Spatial and reverb go through the soft knee at the output edge, 8D is clipped to the sample range.
- Stage: done on Android. `stageEngine/` is a Gradle module built like `opusDecoder`: `buildStageJni` is cacheable, resolves the NDK and CMake from the SDK and builds the ARM ABIs only into `libmochystage.so`. The JNI bridge is `androidApp/.../playback/stage/StageNative.kt` (lazy load of the library and the HRTF asset; any failure leaves Stage off and the card shows it unavailable). `StageAudioProcessor.kt` handles stereo PCM16 only and copies the bytes while the room is off. The portable model, presets and `toStageParams()` are in `shared/.../audiolab/StageSettings.kt`. `tools/stage/make_hrtf.py` rebuilds the asset from the MIT KEMAR compact set (credit in `NOTICE.md`).

Settings page: the Audio Lab row sits next to the equalizer in the sound settings. The cards work on Android, desktop and iOS. On iOS a note says effects do not apply to HLS streams.

## Desktop plan

Engine: `shared/src/desktopMain/kotlin/com/metrolist/shared/playback/ffmpeg/AudioRenderer.kt`. The equalizer runs at line 129 (`val equalized = equalize(data, boostDb)`) and the silence skipper at line 131. Hook: `AudioLabChain.processPcm16` runs on `equalized` between those two lines. The renderer owns the chain and its settings scope, `reset()` runs with the equalizer on flush, and `close()` cancels the scope.

## iOS plan

Engine: `MTAudioProcessingTap` in `shared/src/iosMain/kotlin/com/metrolist/shared/playback/IosAudioProcessingTap.kt`. The tap reads `EqualizerProfiles.configuration` (line 66), and `processIosAudioTap` (line 256) processes the buffers. Hook: `IosAudioTapState.process` runs `AudioLabChain.process` on the float buffer after the equalizer and the spectrum meter. `prepare` configures the chain, `unprepare` stops it, and settings reach the cores from the tap scope. HLS fallback bypasses the tap, so effects are unavailable on HLS streams (see `docs/audio-processing.md`).

## Not done yet

- Stage on desktop and iOS: the card is shown disabled with a note that Stage runs on Android only.
- Stage room and scene drawing (the Canvas views of the room), object editing, the walls and speaker choices in the UI, and surround (3 to 8 channel) downmix.
- Stage speaker detection: the room always uses the headphone model; the reference chooses a speaker model from the output device.
- Stage on a device: the native build and the sound have not been run on hardware, only checked against the reference sources.
- A Mochy module entry for Audio Lab. The module registry only models source, lyrics, look and metadata modules, with no effect-module pattern, so turning a module off cannot reset the effects yet.
- Unit tests: `shared/src/commonTest` is kept small by policy (see `CLAUDE.md`), and the preset and clamping values here are constants.
