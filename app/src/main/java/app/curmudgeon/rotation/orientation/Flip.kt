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

internal class ActiveFlip(val start: FlipStart, var written: SystemRotation) {
    var phase = FlipPhase.TURNING
    var watcher: TurnWatcher? = null
}
