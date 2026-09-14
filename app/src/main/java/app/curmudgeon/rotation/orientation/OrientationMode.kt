// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.pm.ActivityInfo
import android.view.Surface

/** Rotation states the tile and the quick-action notification switch between. */
enum class OrientationMode {
    /** Auto-rotate off, normal portrait phone (the stock tile's "off"). */
    OFF,
    AUTO, PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE;

    /** System-setting values for this mode; [current] supplies the user rotation kept while auto-rotating. */
    fun toSystemRotation(current: SystemRotation): SystemRotation = when (this) {
        OFF -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_0)
        AUTO -> SystemRotation(autoRotate = true, userRotation = current.userRotation)
        PORTRAIT -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_0)
        LANDSCAPE -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_90)
        REVERSE_LANDSCAPE -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_270)
    }

    /**
     * Orientation for the overlay window (a hard lock apps cannot override), or null for no window. Portrait is
     * never hard-locked: that would stop apps (YouTube full screen) from going landscape on their own.
     */
    fun toOverlayOrientation(): Int? = when (this) {
        OFF, AUTO, PORTRAIT -> null
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }

    /** Landscape locks are the ones the hard lock backs; portrait is always a plain system lock. */
    fun isLandscapeLock(): Boolean = this == LANDSCAPE || this == REVERSE_LANDSCAPE

    companion object {
        fun fromSystemRotation(rotation: SystemRotation): OrientationMode = when {
            rotation.autoRotate -> AUTO
            rotation.userRotation == Surface.ROTATION_90 -> LANDSCAPE
            rotation.userRotation == Surface.ROTATION_270 -> REVERSE_LANDSCAPE
            else -> OFF
        }
    }
}
