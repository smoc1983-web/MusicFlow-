package com.musicflow.app

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference

/**
 * Serializes video surface ownership between mini and fullscreen.
 * These screens use different MediaControllers for the same MediaSession.
 * Both surfaces must never be attached to the shared service simultaneously.
 * All methods run on Android's main thread.
 */
@OptIn(UnstableApi::class)
internal object TvVideoSurfaceBridge {
    private var mini: WeakReference<PlayerView>? = null
    private var miniController: WeakReference<MediaController>? = null
    private var full: WeakReference<PlayerView>? = null
    private var mainIsResumed = false
    private var fullscreenRequested = false

    fun onMainResume() {
        mainIsResumed = true
        restoreMini()
    }

    fun onMainPause() {
        mainIsResumed = false
        mini?.get()?.player = null
    }

    fun bindMini(view: PlayerView, controller: MediaController) {
        val previous = mini?.get()
        if (previous != null && previous !== view) previous.player = null
        mini = WeakReference(view)
        miniController = WeakReference(controller)
        if (mainIsResumed && !fullscreenRequested && full?.get() == null) {
            if (view.player !== controller) view.player = controller
        } else if (view.player != null) {
            view.player = null
        }
    }

    fun releaseMini(view: PlayerView) {
        if (view.player != null) view.player = null
        if (mini?.get() === view) {
            mini = null
            miniController = null
        }
    }

    fun requestFullscreen() {
        fullscreenRequested = true
        // Detach before launching the second Activity; it uses a new controller.
        mini?.get()?.player = null
    }

    fun cancelFullscreenRequest() {
        if (full?.get() != null) return
        fullscreenRequested = false
        restoreMini()
    }

    fun bindFullscreen(view: PlayerView, controller: MediaController) {
        requestFullscreen()
        val previous = full?.get()
        if (previous != null && previous !== view) previous.player = null
        full = WeakReference(view)
        if (view.player !== controller) view.player = controller
    }

    fun releaseFullscreen(view: PlayerView) {
        // Keep ownership with fullscreen until the Activity actually exits.
        if (view.player != null) view.player = null
        if (full?.get() === view) full = null
    }

    fun fullscreenDestroyed() {
        full?.get()?.player = null
        full = null
        fullscreenRequested = false
        restoreMini()
    }

    private fun restoreMini() {
        if (!mainIsResumed || fullscreenRequested || full?.get() != null) return
        val view = mini?.get() ?: return
        val controller = miniController?.get() ?: return
        if (view.player !== controller) view.player = controller
    }
}
