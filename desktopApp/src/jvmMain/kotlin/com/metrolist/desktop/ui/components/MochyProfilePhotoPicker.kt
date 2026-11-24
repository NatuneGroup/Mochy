/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.metrolist.desktop.utils.pickDesktopFile
import com.metrolist.desktop.utils.playlistCoverExtensions
import com.metrolist.desktop.utils.readPlaylistCover
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.mochy_profile_choose_photo
import com.metrolist.shared.utils.XdgUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
internal fun rememberMochyProfilePhotoPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.mochy_profile_choose_photo)
    return {
        scope.launch {
            val file =
                try {
                    pickDesktopFile(title, playlistCoverExtensions.toList())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return@launch
            val savedPath = withContext(Dispatchers.IO) { persistDesktopMochyProfilePhoto(file) }
            if (!savedPath.isNullOrBlank()) {
                onPicked(savedPath)
            }
        }
    }
}

private fun persistDesktopMochyProfilePhoto(source: File): String? =
    try {
        val bytes = readPlaylistCover(source)
        val ext = source.extension.lowercase().takeIf { it in playlistCoverExtensions } ?: "jpg"
        val dir = File(XdgUtils.getXdgDataHome(), "metrolist/mochy_profile").apply { mkdirs() }
        val dest = File(dir, "avatar_${System.currentTimeMillis()}.$ext")
        dest.outputStream().use { it.write(bytes) }
        dir
            .listFiles { file -> file.isFile && file.absolutePath != dest.absolutePath }
            ?.forEach { runCatching { it.delete() } }
        dest.absolutePath
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
