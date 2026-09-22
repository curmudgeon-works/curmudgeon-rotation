// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ViewConfiguration
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.ui.flipLabel
import app.curmudgeon.rotation.ui.labelRes

/**
 * Tap runs the tap action from settings (default: rotate once); double-tap runs the Lock action (the second tile's
 * tap, hard landscape by default). Long-press opens the app: the system launches an activity for it, which ejects a
 * fullscreen app to picture-in-picture, so no rotation action can live there. Works without the detection service.
 *
 * When the mechanism's permission is missing the tile stays clickable (STATE_UNAVAILABLE tiles
 * receive no clicks) but shows "Tap to set up" and opens the explanation screen.
 */
class RotationTileService : TileService() {
    override fun onTileAdded() {
        Prefs.tileAdded = true
    }

    override fun onTileRemoved() {
        Prefs.tileAdded = false
    }

    // requestListeningState does nothing while the panel already shows the tile, so a flip ending then redraws it here
    private val onRotationChanged: () -> Unit = { updateTile() }

    override fun onStartListening() {
        Prefs.tileAdded = true
        OrientationController.addListener(onRotationChanged)
        updateTile()
    }

    override fun onStopListening() {
        OrientationController.removeListener(onRotationChanged)
        // the panel is going: a tap still waiting for its double runs now, before the process can be dropped
        pendingTap?.let { tap ->
            handler.removeCallbacks(tap)
            tap.run()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingTap: Runnable? = null

    /**
     * A tap waits [DOUBLE_TAP_MS] for a second one: alone it runs the tap action, as a pair the Lock action. Nothing
     * rotates during the wait, so a double-tap is possible even when the tap action would turn the screen. Taps that
     * open a screen don't wait.
     */
    override fun onClick() {
        pendingTap?.let { tap ->
            handler.removeCallbacks(tap)
            pendingTap = null
            run(Prefs.lockTapAction.perform(this))
            return
        }
        if (!OrientationController.isManualAvailable() || Prefs.tileTapAction == TileAction.OPEN_APP) {
            run(Prefs.tileTapAction.perform(this))
            return
        }
        val tap = Runnable {
            pendingTap = null
            run(Prefs.tileTapAction.perform(this))
        }
        pendingTap = tap
        handler.postDelayed(tap, DOUBLE_TAP_MS)
    }

    /** Finishes an action: opens the screen it asked for, or redraws the tile. */
    private fun run(open: Intent?) {
        if (open != null) {
            launch(open)
            return
        }
        RotationService.sync(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val available = OrientationController.isManualAvailable()
        val mode = OrientationController.currentMode()
        if (available) {
            // the auto-rotate icon, lit while auto-rotate is on; a running flip swaps in the flip icon, lit until the start
            // is back; a Lock (double-tap) shows the Lock tile's icon, lit until released
            val flipping = OrientationController.flipPhase != null
            val locked = OrientationController.isLandscapeForced
            tile.icon = Icon.createWithResource(
                this,
                when {
                    locked -> R.drawable.ic_rotation_landscape
                    flipping -> R.drawable.ic_rotation_off
                    else -> R.drawable.ic_rotation_auto
                },
            )
            val active = flipping || locked || when (Prefs.tileTapAction) {
                TileAction.FLIP -> false
                TileAction.TOGGLE_AUTO_ROTATE -> mode == OrientationMode.AUTO
                else -> mode != OrientationMode.OFF
            }
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            val subtitle = flipLabel(mode) ?: when {
                mode.isLandscapeLock() && !OrientationController.isHardLockAvailable() -> getString(R.string.tile_soft_lock, getString(mode.labelRes()))
                else -> getString(mode.labelRes())
            }
            setLabels(tile, subtitle)
        } else {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_rotation_setup)
            tile.state = Tile.STATE_INACTIVE
            setLabels(tile, getString(R.string.tile_needs_setup))
        }
        tile.updateTile()
    }

    /** Subtitles exist from Android 10; before that the state goes into the label. */
    private fun setLabels(tile: Tile, subtitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_label)
            tile.subtitle = subtitle
        } else {
            tile.label = subtitle
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(activity: Intent) {
        val intent = activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** How long a tap waits for its double: the platform's own double-tap timeout (300 ms). */
        val DOUBLE_TAP_MS = ViewConfiguration.getDoubleTapTimeout().toLong()

        fun component(context: Context) = ComponentName(context, RotationTileService::class.java)

        /** Asks the system to rebind the tile so it redraws with the current state. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, component(context)) }
        }
    }
}
