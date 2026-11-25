// Ported from Mochy Music by realidkroo. Licensed under GPL-3.0.

package com.metrolist.shared.ui.utils

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.combine

@Composable
actual fun HighRefreshWhile(active: () -> Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(active)
    LaunchedEffect(view, lifecycle) {
        val voter = HighRateVoter.of(view) ?: return@LaunchedEffect
        var holding = false
        try {
            // only while the app is on screen, or the tick would wake the app every frame in the background
            combine(snapshotFlow { currentActive() }, lifecycle.currentStateFlow) { wanted, state ->
                wanted && state.isAtLeast(Lifecycle.State.STARTED)
            }.collect { on ->
                if (on == holding) return@collect
                holding = on
                if (on) voter.acquire() else voter.release()
            }
        } finally {
            if (holding) voter.release()
        }
    }
}

// an invisible pixel that votes for the high rate each frame it's redrawn, the way compose votes for a layout that moved
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class HighRateVoter(
    root: ViewGroup,
) {
    private val pixel =
        VoterPixel(root.context, this).apply {
            // the screen's own top rate: the "high" category is only 90 on some 120 Hz phones, and 90 on a 120 Hz panel judders
            requestedFrameRate = root.display
                ?.supportedModes
                ?.maxOfOrNull { it.refreshRate }
                ?.takeIf { it > 0f } ?: View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private var requests = 0
    private var ticking = false

    // a view only votes when it's drawn again, so it's dirtied on every frame while someone wants it
    private val tick =
        object : Runnable {
            override fun run() {
                if (requests == 0) {
                    ticking = false
                    return
                }
                pixel.invalidate()
                pixel.postOnAnimation(this)
            }
        }

    init {
        root.addView(pixel, ViewGroup.LayoutParams(1, 1))
    }

    fun acquire() {
        requests++
        if (!ticking) {
            ticking = true
            pixel.postOnAnimation(tick)
        }
    }

    fun release() {
        requests = (requests - 1).coerceAtLeast(0)
    }

    companion object {
        // one pixel per window, found on the window itself, so a closed window never stays alive through a static map
        fun of(view: View): HighRateVoter? {
            val content = view.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return null
            for (i in 0 until content.childCount) {
                (content.getChildAt(i) as? VoterPixel)?.let { return it.voter }
            }
            return HighRateVoter(content)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class VoterPixel(
    context: Context,
    val voter: HighRateVoter,
) : View(context)
