// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import app.curmudgeon.rotation.orientation.HomeChoice
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.orientation.SystemRotation
import app.curmudgeon.rotation.orientation.SystemRotationStateStore
import app.curmudgeon.rotation.tile.TilePresence

/** Keys of user-visible settings; must match the res/xml/prefs_*.xml settings screens. */
object PrefKeys {
    const val ADVANCED_MODE = "advanced_mode"
    const val DETECTION_METHOD = "detection_method"
    const val RESTORE_ON_LEAVE = "restore_on_leave"
    const val LOCK_RELEASE_ON_TURN = "lock_release_on_turn"
    const val TILE_MECHANISM = "tile_mechanism"
    const val OVERLAY_TYPE = "overlay_type"
    const val IGNORE_LAUNCHER = "ignore_launcher"
    const val IGNORE_LAUNCHER_TILES = "ignore_launcher_tiles"
    const val IGNORE_LAUNCHER_ROTATE = "ignore_launcher_rotate"
    const val IGNORED_PACKAGES = "ignored_packages"
    const val DEBOUNCE_MS = "debounce_ms"
    const val POLL_INTERVAL_MS = "poll_interval_ms"
    const val QUICK_ACTIONS_NOTIFICATION = "quick_actions_notification"
    const val TILE_ROTATE = "tile_rotate"
    const val TILE_LOCK = "tile_lock"
    const val TILE_PORTRAIT = "tile_portrait"
    const val ADD_TILE_ROTATE = "add_tile_rotate"
    const val ADD_TILE_LOCK = "add_tile_lock"
    const val ADD_TILE_PORTRAIT = "add_tile_portrait"
    const val ADD_CUSTOM_TILE = "add_custom_tile"
    const val TILES_DIVIDER = "tiles_divider"
    /** What a built-in tile's taps and long press do: fixed, shown on its screen. */
    const val TILE_ROTATE_TAP = "tile_rotate_tap"
    const val TILE_ROTATE_DOUBLE_TAP = "tile_rotate_double_tap"
    const val TILE_ROTATE_LONG_PRESS = "tile_rotate_long_press"
    const val TILE_LOCK_TAP = "tile_lock_tap"
    const val TILE_LOCK_DOUBLE_TAP = "tile_lock_double_tap"
    const val TILE_LOCK_LONG_PRESS = "tile_lock_long_press"
    const val TILE_PORTRAIT_TAP = "tile_portrait_tap"
    const val TILE_PORTRAIT_DOUBLE_TAP = "tile_portrait_double_tap"
    const val TILE_PORTRAIT_LONG_PRESS = "tile_portrait_long_press"
    /** Custom tile rows on the tiles screen are added in code, keyed this prefix plus the slot. */
    const val CUSTOM_TILE_PREFIX = "custom_tile_"
    const val START_ON_BOOT = "start_on_boot"
    const val EXPORT_RULES = "export_rules"
    const val IMPORT_RULES = "import_rules"
    const val RECENT_LOG = "recent_log"
    const val KEEP_EVENT_LOG = "keep_event_log"
    const val EVENT_LOG = "event_log"
    const val ABOUT = "about"
}

enum class DetectionMethod(val value: String) { ACCESSIBILITY("accessibility"), USAGE_STATS("usage_stats") }

enum class TileMechanism(val value: String) { SYSTEM_SETTING("system"), OVERLAY("overlay") }

/** Lock tile tap: hold landscape until tapped again, or cycle the four locked orientations then release. */
enum class LockTapAction(val value: String) { LANDSCAPE("landscape"), CYCLE("cycle") }

enum class OverlayType(val value: String) { ACCESSIBILITY("accessibility"), APPLICATION("application") }

/**
 * Typed access to settings (default SharedPreferences, edited by the settings screen) and to
 * internal state (a separate private file). Initialised once in the Application.
 */
object Prefs {
    const val DEBOUNCE_DEFAULT = 300L
    const val POLL_INTERVAL_DEFAULT = 1000L
    val DEBOUNCE_RANGE = 0L..5000L
    val POLL_INTERVAL_RANGE = 250L..60_000L

    lateinit var settings: SharedPreferences
        private set
    lateinit var state: SharedPreferences
        private set

    fun init(context: Context) {
        settings = PreferenceManager.getDefaultSharedPreferences(context)
        state = context.getSharedPreferences("state", Context.MODE_PRIVATE)
    }

    var advancedMode: Boolean
        get() = settings.getBoolean(PrefKeys.ADVANCED_MODE, false)
        set(value) = settings.edit { putBoolean(PrefKeys.ADVANCED_MODE, value) }

    val detectionMethod: DetectionMethod
        get() = enumPref(PrefKeys.DETECTION_METHOD, DetectionMethod.ACCESSIBILITY) { it.value }

    val restoreOnLeave: Boolean get() = settings.getBoolean(PrefKeys.RESTORE_ON_LEAVE, true)

    /** The Lock tile ends itself when the phone is turned upright again (after being held sideways). */
    val lockReleaseOnTurn: Boolean get() = settings.getBoolean(PrefKeys.LOCK_RELEASE_ON_TURN, false)

    val tileMechanism: TileMechanism
        get() = enumPref(PrefKeys.TILE_MECHANISM, TileMechanism.OVERLAY) { it.value }

    val overlayType: OverlayType
        get() = enumPref(PrefKeys.OVERLAY_TYPE, OverlayType.ACCESSIBILITY) { it.value }

    /** Per-app rules: on, going to the launcher keeps the app's rule in force there; off, it ends the rule. */
    val ignoreLauncher: Boolean get() = settings.getBoolean(PrefKeys.IGNORE_LAUNCHER, false)

    /** Tiles: on, Lock, Portrait and Flip stay in force on the launcher; off, they're lifted there and a waiting flip ends. */
    val ignoreLauncherTiles: Boolean get() = settings.getBoolean(PrefKeys.IGNORE_LAUNCHER_TILES, true)

    /** Rotate choices: on, the choice is forced on the launcher too; off, it's lifted while the launcher is in front. */
    val ignoreLauncherRotate: Boolean get() = settings.getBoolean(PrefKeys.IGNORE_LAUNCHER_ROTATE, true)

    val ignoredPackages: Set<String>
        get() = parsePackageList(settings.getString(PrefKeys.IGNORED_PACKAGES, "").orEmpty())

    val debounceMs: Long get() = longPref(PrefKeys.DEBOUNCE_MS, DEBOUNCE_DEFAULT, DEBOUNCE_RANGE)

    val pollIntervalMs: Long get() = longPref(PrefKeys.POLL_INTERVAL_MS, POLL_INTERVAL_DEFAULT, POLL_INTERVAL_RANGE)

    val quickActionsNotification: Boolean get() = settings.getBoolean(PrefKeys.QUICK_ACTIONS_NOTIFICATION, false)

    val startOnBoot: Boolean get() = settings.getBoolean(PrefKeys.START_ON_BOOT, false)

    /** Off by default: nothing is written down, not even to logcat. */
    val keepEventLog: Boolean get() = settings.getBoolean(PrefKeys.KEEP_EVENT_LOG, false)

    /** The first-launch prompt for "Modify system settings" has been shown (shown once; the status card covers later). */
    var askedWriteSettings: Boolean
        get() = state.getBoolean("asked_write_settings", false)
        set(value) = state.edit { putBoolean("asked_write_settings", value) }

    /** Set by the tile service when the tile is added or removed; there is no API to query it. */
    var tileAdded: Boolean
        get() = isTileAdded(TilePresence.ROTATE)
        set(value) = setTileAdded(TilePresence.ROTATE, value)

    /** Whether tile [id] is in Quick Settings ([TilePresence]); the Rotate tile keeps its original key. */
    fun isTileAdded(id: String): Boolean = state.getBoolean(tileAddedKey(id), false)

    fun setTileAdded(id: String, added: Boolean) = state.edit { putBoolean(tileAddedKey(id), added) }

    private fun tileAddedKey(id: String) = if (id == TilePresence.ROTATE) "tile_added" else "tile_added_$id"

    /** Last mode chosen from the tile, its long-press or the notification. */
    var manualMode: OrientationMode
        get() = state.getString("manual_mode", null)
            ?.let { name -> OrientationMode.entries.firstOrNull { it.name == name } } ?: OrientationMode.OFF
        set(value) = state.edit { putString("manual_mode", value.name) }

    /** The choice under "Rotate" on the main screen, or null when none is in force (rotation was set elsewhere). */
    var homeChoice: HomeChoice?
        get() = HomeChoice.of(state.getString("home_choice", null))
        set(value) = state.edit { putString("home_choice", value?.value) }

    /** Where the "Lock orientation" arrow points: a Surface rotation, the top of the picture on the phone. */
    var lockArrow: Int
        get() = state.getInt("lock_arrow", 0).coerceIn(0, 3)
        set(value) = state.edit { putInt("lock_arrow", value) }

    /** Per-app rules win over the choice under "Rotate"; off, rules are ignored (and apps aren't watched for them). */
    var rulesWin: Boolean
        get() = state.getBoolean("rules_win", true)
        set(value) = state.edit { putBoolean("rules_win", value) }

    /** The "Per-app rules" list on the main screen is unfolded. */
    var rulesUnfolded: Boolean
        get() = state.getBoolean("rules_unfolded", false)
        set(value) = state.edit { putBoolean("rules_unfolded", value) }

    val rotationStateStore: SystemRotationStateStore = object : SystemRotationStateStore {
        override var snapshot: SystemRotation?
            get() = readRotation("snapshot")
            set(value) = writeRotation("snapshot", value)
        override var lastWritten: SystemRotation?
            get() = readRotation("last_written")
            set(value) = writeRotation("last_written", value)
    }

    /** Splits a user-edited list of package names separated by whitespace or commas. */
    fun parsePackageList(text: String): Set<String> =
        text.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun readRotation(prefix: String): SystemRotation? {
        if (!state.contains("${prefix}_auto")) return null
        return SystemRotation(state.getBoolean("${prefix}_auto", true), state.getInt("${prefix}_user", 0))
    }

    private fun writeRotation(prefix: String, value: SystemRotation?) = state.edit {
        if (value == null) {
            remove("${prefix}_auto")
            remove("${prefix}_user")
        } else {
            putBoolean("${prefix}_auto", value.autoRotate)
            putInt("${prefix}_user", value.userRotation)
        }
    }

    private fun longPref(key: String, default: Long, range: LongRange): Long =
        settings.getString(key, null)?.trim()?.toLongOrNull()?.coerceIn(range) ?: default

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T, value: (T) -> String): T {
        val stored = settings.getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { value(it) == stored } ?: default
    }
}
