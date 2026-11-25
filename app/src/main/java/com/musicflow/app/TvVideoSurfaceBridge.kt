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
    private var mainController: WeakReference<MediaController>? = null

    // Reuse the same connection to the playback session in both Activities.
    // A second MediaController may clear or replace the active video surface.
    fun registerMainController(controller: MediaController) {
        mainController = WeakReference(controller)
    }

    fun unregisterMainController(controller: MediaController?) {
        if (mainController?.get() === controller) mainController = null
    }

    fun sharedController(): MediaController? = mainController?.get()

    private var full: WeakReference<PlayerView>? = null
    private var mainIsResumed = false
    private var fullscreenRequested = false
    // A fullscreen Activity may change the parent orientation as it exits.
    // Ignore that synthetic rotation instead of opening a second Activity.
    private var suppressRotationLaunch = false

    fun allowRotationLaunch(rotatedToLandscape: Boolean, portrait: Boolean): Boolean {
        if (portrait) suppressRotationLaunch = false
        return rotatedToLandscape && !suppressRotationLaunch &&
            !fullscreenRequested && full?.get() == null
    }

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

    fun requestFullscreen(): Boolean {
        if (fullscreenRequested || full?.get() != null) return false
        fullscreenRequested = true
        // Detach before starting the fullscreen Activity.
        mini?.get()?.player = null
        return true
    }

    fun cancelFullscreenRequest() {
        if (full?.get() != null) return
        fullscreenRequested = false
        restoreMini()
    }

    fun bindFullscreen(view: PlayerView, controller: MediaController) {
        fullscreenRequested = true
        val previous = full?.get()
        if (previous != null && previous !== view) previous.player = null
        val oldMini = mini?.get()
        if (oldMini != null && oldMini.player === controller) {
            // Media3 transfers the surface without resetting the playing item.
            PlayerView.switchTargetView(controller, oldMini, view)
        } else {
            if (oldMini?.player != null) oldMini.player = null
            if (view.player !== controller) view.player = controller
        }
        full = WeakReference(view)
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
        suppressRotationLaunch = true
        restoreMini()
    }

    private fun restoreMini() {
        if (!mainIsResumed || fullscreenRequested || full?.get() != null) return
        val view = mini?.get() ?: return
        val controller = miniController?.get() ?: return
        if (view.player !== controller) view.player = controller
    }
}
