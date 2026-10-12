// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.pm.ActivityInfo
import android.view.Surface

/**
 * The rotation picked under "Rotate" on the main screen: the user's own setting, which the tiles return to. Every
 * choice but [AUTO] is a hard lock (the overlay window), so apps can't override it. [LOCKED] freezes the rotation
 * the arrow shows ([arrow]: a Surface rotation, where the top of the picture sits on the phone).
 */
enum class HomeChoice(val value: String) {
    /** Android's own auto-rotate; apps may refuse to turn. */
    AUTO("auto"),
    /** Follows the sensor to all four sides, upside down too. */
    FOUR_WAY("four_way"),
    LOCKED("locked"),
    /** Either portrait side, following the sensor between them. */
    PORTRAIT_FLIP("portrait_flip"),
    /** Either landscape side, following the sensor between them. */
    LANDSCAPE_FLIP("landscape_flip");

    val needsHardLock: Boolean get() = this != AUTO

    /** System-setting values this choice writes; [current] supplies the user rotation kept while auto-rotating. */
    fun system(current: SystemRotation, arrow: Int): SystemRotation = when (this) {
        AUTO, FOUR_WAY -> SystemRotation(autoRotate = true, userRotation = current.userRotation)
        LOCKED -> SystemRotation(autoRotate = false, userRotation = arrow)
        PORTRAIT_FLIP -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_0)
        LANDSCAPE_FLIP -> SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_90)
    }

    /** Orientation for the overlay window, or null for none. */
    fun overlay(arrow: Int): Int? = when (this) {
        AUTO -> null
        FOUR_WAY -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        LOCKED -> lockedOrientation(arrow)
        PORTRAIT_FLIP -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        LANDSCAPE_FLIP -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    companion object {
        fun of(value: String?): HomeChoice? = entries.firstOrNull { it.value == value }

        /** The arrow's next direction, a quarter turn clockwise: top → right → bottom → left. */
        fun nextArrow(arrow: Int): Int = (arrow + 1) % 4

        /** The exact orientation that shows the picture's top where [arrow] points. */
        fun lockedOrientation(arrow: Int): Int = when (arrow) {
            Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
