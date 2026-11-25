// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.metrolist.shared.audiolab.ReverbSettings
import com.metrolist.shared.audiolab.dsp.ReverbCore

/** Reverb room for mono or stereo PCM16, running the shared [ReverbCore]. Other formats pass through. */
@OptIn(UnstableApi::class)
internal class ReverbAudioProcessor : Pcm16CoreAudioProcessor<ReverbCore>(ReverbCore(), 1..2, softKnee = true) {
    // Read once per buffer; when off, audio passes through after a short fade, like the equalizer.
    fun update(newSettings: ReverbSettings) {
        core.update(newSettings)
    }
}
