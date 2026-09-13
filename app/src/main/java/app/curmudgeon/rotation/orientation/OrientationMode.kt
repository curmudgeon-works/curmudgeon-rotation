// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.pm.ActivityInfo
import android.view.Surface

/** Rotation states the tile and the quick-action notification switch between. */
enum class OrientationMode {
    AUTO, PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE;

    /** System-setting values for this mode; [current] supplies the user rotation kept while auto-rotating. */
    fun toSystemRotation(current: SystemRotation): SystemRotation = when (this) {
        AUTO -> SystemRotation(autoRotate = true, userRotation = current.userRotation)
        PORTRAIT -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_0)
        LANDSCAPE -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_90)
        REVERSE_LANDSCAPE -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_270)
    }

    /** Orientation for the overlay window, or null for AUTO (no window: follow the system). */
    fun toOverlayOrientation(): Int? = when (this) {
        AUTO -> null
        PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }

    companion object {
        fun fromSystemRotation(rotation: SystemRotation): OrientationMode = when {
            rotation.autoRotate -> AUTO
            rotation.userRotation == Surface.ROTATION_90 -> LANDSCAPE
            rotation.userRotation == Surface.ROTATION_270 -> REVERSE_LANDSCAPE
            else -> PORTRAIT
        }
    }
}
