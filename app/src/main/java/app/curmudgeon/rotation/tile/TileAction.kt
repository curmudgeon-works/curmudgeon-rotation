// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.content.Context
import android.content.Intent
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.ui.MainActivity
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic

/** What a tile tap or long-press does; chosen in settings. [value] is the stored preference value. */
enum class TileAction(val value: String) {
    /** Flips what is on screen; the sensor notices the phone turning to match and back: [OrientationController.flip]. */
    FLIP("flip"),

    /** Locked portrait ⇄ locked landscape, flipping whatever is on screen now. */
    TOGGLE_ORIENTATION("toggle_orientation"),

    /** Off → Auto → Portrait → Landscape → Off. */
    CYCLE("cycle"),

    /** Off → Auto → Portrait → Landscape → Reverse landscape → Off. */
    CYCLE_WITH_REVERSE("cycle_with_reverse"),

    /** Auto-rotate off when it is on, otherwise on (dropping any lock). */
    TOGGLE_AUTO_ROTATE("toggle_auto_rotate"),

    OPEN_APP("open_app");

    /**
     * Mode to switch to from [current], or null for [FLIP] and [OPEN_APP], which are not a mode. [displayLandscape]
     * is whether the screen is showing landscape right now; the orientation toggle uses it unless a lock is already
     * set, so a quick second tap flips the lock even before the screen has finished rotating.
     */
    fun target(current: OrientationMode, displayLandscape: Boolean): OrientationMode? = when (this) {
        TOGGLE_ORIENTATION -> when (current) {
            OrientationMode.PORTRAIT -> OrientationMode.LANDSCAPE
            OrientationMode.LANDSCAPE, OrientationMode.REVERSE_LANDSCAPE -> OrientationMode.PORTRAIT
            OrientationMode.OFF, OrientationMode.AUTO -> if (displayLandscape) OrientationMode.PORTRAIT else OrientationMode.LANDSCAPE
        }
        CYCLE -> nextInCycle(current, CYCLE_ORDER)
        CYCLE_WITH_REVERSE -> nextInCycle(current, CYCLE_ORDER + OrientationMode.REVERSE_LANDSCAPE)
        TOGGLE_AUTO_ROTATE -> if (current == OrientationMode.AUTO) OrientationMode.OFF else OrientationMode.AUTO
        FLIP, OPEN_APP -> null
    }

    /** Performs the action. Returns a screen to open instead (the app, or setup when permission is missing), or null when done. */
    fun perform(context: Context): Intent? {
        val done = when (this) {
            OPEN_APP -> return Intent(context, MainActivity::class.java)
            FLIP -> OrientationController.flip()            else -> OrientationController.setManualMode(
                target(OrientationController.currentMode(), OrientationController.isDisplayLandscape())!!,
            )
        }
        return if (done) null else SetupActivity.intent(context, SetupTopic.WRITE_SETTINGS)
    }

    companion object {
        private val CYCLE_ORDER = listOf(OrientationMode.OFF, OrientationMode.AUTO, OrientationMode.PORTRAIT, OrientationMode.LANDSCAPE)

        /** A mode outside the cycle (reverse landscape set elsewhere) goes back to Off. */
        private fun nextInCycle(current: OrientationMode, order: List<OrientationMode>) = order[(order.indexOf(current) + 1) % order.size]
    }
}
