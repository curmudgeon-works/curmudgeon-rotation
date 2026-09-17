// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.pm.ActivityInfo
import android.view.Surface

/**
 * Orientations the "Lock" tile goes through in cycle mode, in tap order, each a hard lock. The first is landscape so
 * the first tap does what the plain landscape Lock does; the tap after the last releases the lock.
 */
enum class LockStep(val userRotation: Int, val overlayOrientation: Int) {
    LANDSCAPE(Surface.ROTATION_90, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    UPSIDE_DOWN(Surface.ROTATION_180, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    REVERSE_LANDSCAPE(Surface.ROTATION_270, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    PORTRAIT(Surface.ROTATION_0, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    /** The step after this one, or null when this is the last (the next tap releases the lock). */
    fun next(): LockStep? = entries.getOrNull(ordinal + 1)
}
