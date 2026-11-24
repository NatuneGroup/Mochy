/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.ui.component

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.metrolist.android.utils.readPlaylistCover
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun rememberMochyProfilePhotoPicker(onPicked: (uri: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val path = withContext(Dispatchers.IO) { persistMochyProfilePhoto(context, uri) }
                if (!path.isNullOrBlank()) {
                    onPicked(path)
                }
            }
        }
    return { launcher.launch(arrayOf("image/*")) }
}

private fun persistMochyProfilePhoto(
    context: Context,
    uri: Uri,
): String? =
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readPlaylistCover) ?: return null
        val dir = File(context.filesDir, "mochy_profile").apply { mkdirs() }
        val dest = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
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
