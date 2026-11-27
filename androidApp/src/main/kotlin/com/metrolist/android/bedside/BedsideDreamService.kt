// Ported from Sora (github.com/shizenworks/sora)'s AOD screensaver, drawn with Mochy's bedside clock. Licensed under GPL-3.0.

package com.metrolist.android.bedside

import android.content.ComponentName
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.metrolist.android.playback.MusicService
import com.metrolist.shared.ui.player.mochy.BedsideClock

/**
 * The phone's screensaver while it charges or sits docked: Mochy's bedside clock with the playing song under it,
 * played and skipped from there. Picked in the system's screen saver settings.
 */
class BedsideDreamService : DreamService() {
    // a DreamService isn't an activity, so Compose gets its owners from here
    private class Owner :
        LifecycleOwner,
        SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun restore(state: Bundle?) = savedState.performRestore(state)
    }

    private val owner = Owner()
    private var metadata by mutableStateOf<MediaMetadata?>(null)
    private var playing by mutableStateOf(false)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener =
        object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                metadata = mediaMetadata
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }

    override fun onCreate() {
        super.onCreate()
        owner.restore(null)
        isInteractive = true
        isFullscreen = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    BedsideClock(
                        title = metadata?.title?.toString(),
                        artist = metadata?.artist?.toString(),
                        isPlaying = playing,
                        onTogglePlay = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                        onDismiss = ::finish,
                        artworkUrl = metadata?.artworkUri?.toString(),
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        inDialog = false,
                    )
                }
            },
        )
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        connect()
    }

    // the player's own session, the way a notification or a watch reaches it
    private fun connect() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val built = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = built
                metadata = built.mediaMetadata
                playing = built.isPlaying
                built.addListener(listener)
            },
            MoreExecutors.directExecutor(),
        )
    }

    override fun onDetachedFromWindow() {
        controller?.removeListener(listener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        controllerFuture = null
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
    }
}
