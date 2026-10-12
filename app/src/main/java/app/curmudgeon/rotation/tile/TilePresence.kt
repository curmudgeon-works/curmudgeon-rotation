// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import app.curmudgeon.rotation.settings.Prefs

/**
 * Whether each of our tiles is in the Quick Settings panel. There is no API to ask, so the tile services record it
 * ([onAdded], [onRemoved]), and "Add to Quick Settings" switched off takes a tile out ([remove]). [id] is "rotate",
 * "lock", "portrait" or [customId].
 */
object TilePresence {
    const val ROTATE = "rotate"
    const val LOCK = "lock"
    const val PORTRAIT = "portrait"

    /** How long a tile's service stays switched off so Quick Settings drops the tile before it comes back. */
    private const val REMOVE_MS = 2000L

    private val handler = Handler(Looper.getMainLooper())

    fun customId(slot: Int) = "custom_$slot"

    fun isAdded(id: String): Boolean = Prefs.isTileAdded(id)

    /** From the tile service: added, or showing in the panel (so it must be there). */
    fun onAdded(id: String) = Prefs.setTileAdded(id, true)

    fun onRemoved(id: String) = Prefs.setTileAdded(id, false)

    /**
     * Takes the tile out of Quick Settings: switching its service off makes the panel drop it, then [restore] switches
     * it back as it should be, so it can be added again. A process ended before that is covered at start, by
     * [restoreBuiltIns] and [CustomTileStore.init].
     */
    fun remove(context: Context, id: String, component: ComponentName, restore: () -> Unit) {
        context.packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        onRemoved(id)
        handler.postDelayed(restore, REMOVE_MS)
    }

    /** Switches a built-in tile's service back on after a [remove]. */
    fun restoreBuiltIn(context: Context, component: ComponentName) {
        val packageManager = context.packageManager
        if (packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
    }

    /** At start: the built-in tiles' services back on, in case the process ended during a [remove]. */
    fun restoreBuiltIns(context: Context) = listOf(
        RotationTileService.component(context),
        LandscapeLockTileService.component(context),
        PortraitLockTileService.component(context),
    ).forEach { restoreBuiltIn(context, it) }
}
