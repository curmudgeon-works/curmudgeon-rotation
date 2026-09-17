// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.view.Surface

/** Stage of a tile flip ([OrientationController.flip]). */
enum class FlipPhase {
    /** Locked the other way, waiting for the phone (or screen) to turn. */
    TURNING,

    /** Auto-rotate is on, waiting for the phone (or screen) to turn back, then the start is restored. */
    TURNED,
}

/** Settings a flip returns to. */
data class FlipStart(val rotation: SystemRotation, val mode: OrientationMode) {
    /** Which way the starting lock points, or null when auto-rotate was on (no fixed orientation). */
    fun lockedLandscape(): Boolean? =
        if (rotation.autoRotate) null else rotation.userRotation == Surface.ROTATION_90 || rotation.userRotation == Surface.ROTATION_270
}

/** How a flip notices the turn: the orientation sensor (the phone turned) or the display (the screen turned). */
interface TurnWatcher {
    fun start()
    fun stop()
}

/**
 * [startedOver] is the app the flip began over, as far as foreground detection knew: null when it had seen nothing yet
 * (detection off, process just started, or only filtered apps so far).
 */
internal class ActiveFlip(val start: FlipStart, var written: SystemRotation, val toLandscape: Boolean, var startedOver: String? = null) {
    var phase = FlipPhase.TURNING
    var watcher: TurnWatcher? = null

    /**
     * Still waiting for the first turn while [foreground], a different app, is on screen. The sensor can't tell
     * "lying down, keep holding" from "never turned the phone"; having left the app can. Once turned, the flip
     * ends by itself when the phone is held upright again, wherever that happens.
     *
     * A flip that doesn't know where it began takes the first app it hears of as its own: closing the Quick
     * Settings panel announces the app underneath again, so that is normally the one the tile was tapped over.
     */
    fun leftBehindBy(foreground: String): Boolean {
        val began = startedOver ?: run {
            startedOver = foreground
            return false
        }
        return phase == FlipPhase.TURNING && began != foreground
    }
}
