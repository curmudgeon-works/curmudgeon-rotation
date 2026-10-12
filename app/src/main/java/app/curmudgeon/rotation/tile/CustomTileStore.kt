// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.detect.EventLog
import app.curmudgeon.rotation.orientation.HomeChoice
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.settings.LockTapAction
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The user's custom tiles, persisted as JSON in the app's private SharedPreferences. Each one switches its manifest
 * slot ([CustomTileSlots]) on, so it shows in the Quick Settings editor; deleting switches the slot off, which takes
 * the tile out of Quick Settings. Main thread only.
 */
object CustomTileStore {
    private const val KEY = "custom_tiles_json"
    private const val TAG = "CustomTileStore"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var cache: List<CustomTile> = emptyList()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun init(context: Context, statePrefs: SharedPreferences) {
        appContext = context.applicationContext
        prefs = statePrefs
        cache = prefs.getString(KEY, null)?.let { json ->
            try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { CustomTile.fromJson(array.getJSONObject(it)) }
            } catch (e: JSONException) {
                Log.e(TAG, "Stored custom tiles unreadable, starting empty", e)
                null
            }
        } ?: emptyList()
        // the slots switched on are exactly the stored tiles (a slot left on by a failed save would show a dead tile)
        (0 until CustomTileSlots.COUNT).forEach { slot -> setSlotEnabled(slot, find(slot) != null) }
    }

    fun all(): List<CustomTile> = cache

    fun find(slot: Int): CustomTile? = cache.firstOrNull { it.slot == slot }

    /** A slot for a new tile, or null when all [CustomTileSlots.COUNT] are in use. */
    fun freeSlot(): Int? = CustomTileSlots.free(cache.map { it.slot })

    fun put(tile: CustomTile) {
        save(cache.filterNot { it.slot == tile.slot } + tile)
        setSlotEnabled(tile.slot, true)
        CustomTileService.requestUpdate(appContext, tile.slot)
    }

    fun remove(slot: Int) {
        save(cache.filterNot { it.slot == slot })
        setSlotEnabled(slot, false)
        TilePresence.onRemoved(TilePresence.customId(slot))
    }

    /** Takes the tile in [slot] out of Quick Settings, keeping the tile ([TilePresence.remove]). */
    fun removeFromQuickSettings(slot: Int) = TilePresence.remove(
        appContext,
        TilePresence.customId(slot),
        CustomTileService.component(appContext, slot),
    ) { setSlotEnabled(slot, find(slot) != null) }

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    /** Asks every custom tile showing in Quick Settings to redraw. */
    fun requestUpdates(context: Context) = cache.forEach { CustomTileService.requestUpdate(context, it.slot) }

    /**
     * The tile's single or [double] tap. A Rotate choice is picked, remembering what was in force (kept while moving
     * between this tile's own choices); when it is already in force, goes back to what was remembered (the system
     * default when nothing is). Any other press runs its action. Returns the screen to open instead (setup when a
     * permission is missing, or the app), or null when done.
     */
    fun press(context: Context, slot: Int, double: Boolean): Intent? {
        val tile = find(slot) ?: return null
        val press = (if (double) tile.doubleTap else tile.tap) ?: return null
        val target = press.target ?: run {
            EventLog.log("custom tile ${tile.name}: ${if (double) "double-tap" else "tap"} → $press")
            return press.perform(context)
        }
        val selected = OrientationController.selectedHome()
        if (target.isInForce(selected, Prefs.lockArrow)) {
            val back = tile.back
            EventLog.log("custom tile ${tile.name}: off → back to ${back?.choice ?: "system default"}")
            put(tile.copy(back = null))
            if (back?.choice == null) {
                OrientationController.setSystemDefault(back?.system)
                return null
            }
            val arrow = Prefs.lockArrow
            Prefs.lockArrow = back.arrow
            if (OrientationController.setHome(back.choice)) return null
            Prefs.lockArrow = arrow
            return setupIntent(context)
        }
        val back = tile.back?.takeIf { tile.targets.any { it.isInForce(selected, Prefs.lockArrow) } } ?: TileBack(
            selected,
            Prefs.lockArrow,
            if (selected == null && OrientationController.isSystemDefault()) OrientationController.systemRotation() else null,
        )
        EventLog.log("custom tile ${tile.name}: on → $target")
        val arrow = Prefs.lockArrow
        target.arrow?.let { Prefs.lockArrow = it }
        if (!OrientationController.setHome(target.choice)) {
            Prefs.lockArrow = arrow
            return setupIntent(context)
        }
        put(tile.copy(back = back))
        return null
    }

    /** Whether [tile] shows as on: one of its Rotate choices is in force, or one of its actions is running. */
    fun isOn(tile: CustomTile): Boolean =
        tile.targets.any { it.isInForce(OrientationController.selectedHome(), Prefs.lockArrow) } ||
            listOfNotNull(tile.tap, tile.doubleTap).any { it.isRunning() }

    /** A one-shot press ([TilePress.target] null): the built-in tiles' actions. */
    private fun TilePress.perform(context: Context): Intent? = when (this) {
        TilePress.LANDSCAPE_LOCK -> LockTapAction.LANDSCAPE.perform(context)
        TilePress.CYCLE_LOCKS -> LockTapAction.CYCLE.perform(context)
        TilePress.PORTRAIT_LOCK -> if (OrientationController.toggleForcedPortrait()) null else setupIntent(context)
        else -> TileAction.entries.first { it.value == value }.perform(context)
    }

    private fun TilePress.isRunning(): Boolean = when (this) {
        TilePress.FLIP -> OrientationController.flipPhase != null
        TilePress.LANDSCAPE_LOCK -> OrientationController.isLandscapeForced
        TilePress.PORTRAIT_LOCK -> OrientationController.isPortraitForced
        TilePress.CYCLE_LOCKS -> OrientationController.isLandscapeForced || OrientationController.isPortraitForced
        TilePress.TOGGLE_AUTO_ROTATE -> OrientationController.currentMode() == OrientationMode.AUTO
        TilePress.TOGGLE_ORIENTATION, TilePress.CYCLE, TilePress.CYCLE_WITH_REVERSE ->
            OrientationController.currentMode() != OrientationMode.OFF
        else -> false
    }

    private fun setupIntent(context: Context): Intent {
        val topic = if (!Permissions.canWriteSettings(context)) SetupTopic.WRITE_SETTINGS else SetupTopic.ACCESSIBILITY
        return SetupActivity.intent(context, topic)
    }

    private fun setSlotEnabled(slot: Int, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = CustomTileService.component(appContext, slot)
        if (appContext.packageManager.getComponentEnabledSetting(component) == state) return
        appContext.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }

    private fun save(tiles: List<CustomTile>) {
        cache = tiles.sortedBy { it.slot }
        prefs.edit { putString(KEY, JSONArray(cache.map { it.toJson() }).toString()) }
        listeners.forEach { it() }
    }
}

@DrawableRes
fun TileIcon.drawableRes(): Int = when (this) {
    TileIcon.ROTATION -> R.drawable.ic_screen_rotation
    TileIcon.AUTO -> R.drawable.ic_rotation_auto
    TileIcon.LOCKED -> R.drawable.ic_rotation_off
    TileIcon.PORTRAIT -> R.drawable.ic_rotation_portrait
    TileIcon.LANDSCAPE -> R.drawable.ic_rotation_landscape
    TileIcon.REVERSE_LANDSCAPE -> R.drawable.ic_rotation_reverse_landscape
    TileIcon.PHONE -> R.drawable.ic_rotate_phone
    TileIcon.PLAY -> R.drawable.ic_tile_play
    TileIcon.GAME -> R.drawable.ic_tile_game
    TileIcon.BOOK -> R.drawable.ic_tile_book
    TileIcon.CAMERA -> R.drawable.ic_tile_camera
    TileIcon.CAR -> R.drawable.ic_tile_car
    TileIcon.BED -> R.drawable.ic_tile_bed
    TileIcon.STAR -> R.drawable.ic_tile_star
}

/** The target's label for the editor and the tile's subtitle. */
fun TileTarget.labelRes(): Int = when (this) {
    TileTarget.AUTO -> R.string.rotate_auto
    TileTarget.FOUR_WAY -> R.string.rotate_four_way
    TileTarget.LOCK_TOP -> R.string.custom_tile_lock_top
    TileTarget.LOCK_RIGHT -> R.string.custom_tile_lock_right
    TileTarget.LOCK_BOTTOM -> R.string.custom_tile_lock_bottom
    TileTarget.LOCK_LEFT -> R.string.custom_tile_lock_left
    TileTarget.PORTRAIT_FLIP -> R.string.rotate_portrait_flip
    TileTarget.LANDSCAPE_FLIP -> R.string.rotate_landscape_flip
}

/** The press's short name: the tile's subtitle and the editor's row value. */
fun TilePress.labelRes(): Int = target?.labelRes() ?: when (this) {
    TilePress.FLIP -> R.string.press_flip
    TilePress.LANDSCAPE_LOCK -> R.string.press_landscape_lock
    TilePress.PORTRAIT_LOCK -> R.string.press_portrait_lock
    TilePress.CYCLE_LOCKS -> R.string.press_cycle_locks
    TilePress.TOGGLE_ORIENTATION -> R.string.press_toggle_orientation
    TilePress.CYCLE -> R.string.press_cycle
    TilePress.CYCLE_WITH_REVERSE -> R.string.press_cycle_with_reverse
    TilePress.TOGGLE_AUTO_ROTATE -> R.string.press_toggle_auto_rotate
    else -> R.string.press_open_app
}

/** Whether the target needs the hard lock (everything but Auto-rotate). */
val TileTarget.needsHardLock: Boolean get() = choice != HomeChoice.AUTO
