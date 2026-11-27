package com.metrolist.android.playback

import android.net.ConnectivityManager
import com.metrolist.android.App
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.shared.settings.Settings

// the quality chosen for mobile data while the phone is on a metered network, the main one otherwise
internal fun playbackAudioQuality(): AudioQuality {
    val cellular = Settings.shared.audioQualityCellular
    val metered =
        cellular.isNotBlank() &&
            runCatching { App.instance.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true }.getOrDefault(false)
    val chosen = if (metered) cellular else Settings.shared.audioQuality
    return try {
        AudioQuality.valueOf(chosen)
    } catch (_: IllegalArgumentException) {
        AudioQuality.AUTO
    }
}
