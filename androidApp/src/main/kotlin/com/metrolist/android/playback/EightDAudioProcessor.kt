// Ported and adapted from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.metrolist.shared.audiolab.EightDSettings
import com.metrolist.shared.audiolab.dsp.EightDCore

/** Slow 8D pan for stereo PCM16, running the shared [EightDCore]. Other formats pass through. */
@OptIn(UnstableApi::class)
internal class EightDAudioProcessor : Pcm16CoreAudioProcessor<EightDCore>(EightDCore(), 2..2, softKnee = false) {
    // Read once per buffer; when off, audio passes through after a short fade, like the equalizer.
    fun update(newSettings: EightDSettings) {
        core.update(newSettings)
    }
}
