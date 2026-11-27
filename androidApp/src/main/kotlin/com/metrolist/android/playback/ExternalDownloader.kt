// Ported from Sora (github.com/shizenworks/sora), itself after Neiro. Licensed under GPL-3.0.

package com.metrolist.android.playback

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a song to another downloader app as its YouTube Music address: the plain view intent first, then a
 * share, then the app's own launch intent, whichever the downloader accepts. Returns whether an app took it.
 */
internal fun handToExternalDownloader(
    context: Context,
    packageName: String,
    songId: String,
): Boolean {
    val url = "https://music.youtube.com/watch?v=$songId"
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (view.resolveActivity(context.packageManager) != null) {
        context.startActivity(view)
        return true
    }
    val share =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .setPackage(packageName)
    if (share.resolveActivity(context.packageManager) != null) {
        context.startActivity(share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}
