/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.uikit.LocalUIViewController
import com.metrolist.shared.playlist.storeIosPlaylistCover
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.mobile.pickIosPlaylistCover
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun rememberIosMochyProfilePhotoPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    val presenter = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val bytes =
                try {
                    pickIosPlaylistCover(presenter)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return@launch
            val previous = Settings.shared.mochyProfilePhotoUri.takeIf { it.isNotBlank() }
            var savedPath: String? = null
            try {
                storeIosPlaylistCover(bytes = bytes, previousPath = previous) { saved ->
                    savedPath = saved
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Ignore invalid or unreadable images without overwriting the existing photo.
            }
            savedPath?.takeIf { it.isNotBlank() }?.let(onPicked)
        }
    }
}
