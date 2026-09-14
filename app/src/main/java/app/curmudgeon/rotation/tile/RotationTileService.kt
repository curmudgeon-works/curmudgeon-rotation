// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic
import app.curmudgeon.rotation.ui.iconRes
import app.curmudgeon.rotation.ui.labelRes

/**
 * Tap cycles Off → Auto → Portrait → Landscape (→ Reverse landscape) → Off. Long-press is handled by
 * [TileLongPressActivity] (toggles auto-rotate). Works without the detection service.
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

    override fun onStartListening() {
        Prefs.tileAdded = true
        updateTile()
    }

    override fun onClick() {
        val next = TileCycle.next(OrientationController.currentMode(), Prefs.tileCycleIncludesReverse)
        if (!OrientationController.isManualAvailable() || !OrientationController.setManualMode(next)) {
            openSetup()
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
            tile.icon = Icon.createWithResource(this, mode.iconRes())
            tile.state = if (mode == OrientationMode.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            val locked = mode == OrientationMode.PORTRAIT || mode == OrientationMode.LANDSCAPE || mode == OrientationMode.REVERSE_LANDSCAPE
            val subtitle = getString(mode.labelRes())
            setLabels(tile, if (locked && !OrientationController.isHardLockAvailable()) getString(R.string.tile_soft_lock, subtitle) else subtitle)
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
    private fun openSetup() {
        val topic = SetupTopic.WRITE_SETTINGS
        val intent = SetupActivity.intent(this, topic).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, RotationTileService::class.java)

        /** Asks the system to rebind the tile so it redraws with the current state. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, component(context)) }
        }
    }
}
