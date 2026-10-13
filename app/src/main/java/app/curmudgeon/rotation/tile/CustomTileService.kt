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
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.service.RotationService

/**
 * A custom tile ([CustomTile]) in Quick Settings slot [slot]: name and icon as the user set them; single and double
 * tap do what the user picked ([CustomTileStore.press]). With a double-tap set, a tap waits for a second one, like the
 * Rotate tile. Long-press opens the app, like the other tiles. One subclass per slot, because each tile needs its own
 * manifest entry.
 */
abstract class CustomTileService(private val slot: Int) : TileService() {
    // requestListeningState does nothing while the panel already shows the tile, so changes then redraw it here
    private val onChanged: () -> Unit = { updateTile() }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingTap: Runnable? = null

    override fun onTileAdded() = TilePresence.onAdded(TilePresence.customId(slot))

    override fun onTileRemoved() = TilePresence.onRemoved(TilePresence.customId(slot))

    override fun onStartListening() {
        TilePresence.onAdded(TilePresence.customId(slot))
        OrientationController.addListener(onChanged)
        CustomTileStore.addListener(onChanged)
        updateTile()
    }

    override fun onStopListening() {
        OrientationController.removeListener(onChanged)
        CustomTileStore.removeListener(onChanged)
        // the panel is going: a tap still waiting for its double runs now, before the process can be dropped
        pendingTap?.let { tap ->
            handler.removeCallbacks(tap)
            tap.run()
        }
    }

    override fun onClick() {
        pendingTap?.let { tap ->
            handler.removeCallbacks(tap)
            pendingTap = null
            run(double = true)
            return
        }
        if (CustomTileStore.find(slot)?.doubleTap == null) {
            run(double = false)
            return
        }
        val tap = Runnable {
            pendingTap = null
            run(double = false)
        }
        pendingTap = tap
        handler.postDelayed(tap, RotationTileService.DOUBLE_TAP_MS)
    }

    private fun run(double: Boolean) {
        CustomTileStore.press(this, slot, double)?.let { launch(it); return }
        RotationService.sync(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val custom = CustomTileStore.find(slot)
        if (custom == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateSafely()
            return
        }
        tile.icon = Icon.createWithResource(this, custom.icon.drawableRes())
        tile.label = custom.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(custom.tap.labelRes())
        tile.state = if (CustomTileStore.isOn(custom)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateSafely()
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
        private val CLASSES = listOf(
            CustomTileService1::class.java, CustomTileService2::class.java, CustomTileService3::class.java,
            CustomTileService4::class.java, CustomTileService5::class.java, CustomTileService6::class.java,
            CustomTileService7::class.java, CustomTileService8::class.java,
        )

        fun component(context: Context, slot: Int) = ComponentName(context, CLASSES[slot])

        fun requestUpdate(context: Context, slot: Int) {
            runCatching { requestListeningState(context, component(context, slot)) }
        }
    }
}

class CustomTileService1 : CustomTileService(0)
class CustomTileService2 : CustomTileService(1)
class CustomTileService3 : CustomTileService(2)
class CustomTileService4 : CustomTileService(3)
class CustomTileService5 : CustomTileService(4)
class CustomTileService6 : CustomTileService(5)
class CustomTileService7 : CustomTileService(6)
class CustomTileService8 : CustomTileService(7)
