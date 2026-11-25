package com.metrolist.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The app's profile button, provided while Home draws Mochy's own top bar instead of the shared app bar.
 * Null where the shared app bar stays (TV and desktop layouts).
 */
val LocalMochyHomeAccountButton = compositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }
