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
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic

/**
 * Second tile ("Lock"), for apps that insist on portrait (HBO Max): tap forces landscape with the hard lock, tap again puts
 * the previous rotation back ([OrientationController.toggleForcedLandscape]). Long-press opens the app.
 * Without the permissions it needs, a tap opens the matching explanation screen instead.
 */
class LandscapeLockTileService : TileService() {
    override fun onStartListening() = updateTile()

    override fun onClick() {
        if (OrientationController.toggleForcedLandscape()) {
            RotationService.sync(this)
            updateTile()
            return
        }
        val topic = when {
            !Permissions.canWriteSettings(this) -> SetupTopic.WRITE_SETTINGS
            else -> SetupTopic.ACCESSIBILITY
        }
        launch(SetupActivity.intent(this, topic))
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_rotation_landscape)
        tile.label = getString(R.string.tile_force_landscape_label)
        tile.state = if (OrientationController.isLandscapeForced) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
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
        fun component(context: Context) = ComponentName(context, LandscapeLockTileService::class.java)

        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, component(context)) }
        }
    }
}
