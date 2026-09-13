// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.orientation.SystemRotation
import app.curmudgeon.rotation.orientation.SystemRotationStateStore

/** Keys of user-visible settings; must match res/xml/preferences.xml. */
object PrefKeys {
    const val ADVANCED_MODE = "advanced_mode"
    const val DETECTION_METHOD = "detection_method"
    const val RESTORE_ON_LEAVE = "restore_on_leave"
    const val TILE_CYCLE = "tile_cycle"
    const val TILE_MECHANISM = "tile_mechanism"
    const val OVERLAY_TYPE = "overlay_type"
    const val IGNORE_LAUNCHER = "ignore_launcher"
    const val IGNORED_PACKAGES = "ignored_packages"
    const val DEBOUNCE_MS = "debounce_ms"
    const val POLL_INTERVAL_MS = "poll_interval_ms"
    const val QUICK_ACTIONS_NOTIFICATION = "quick_actions_notification"
    const val START_ON_BOOT = "start_on_boot"
    const val EXPORT_RULES = "export_rules"
    const val IMPORT_RULES = "import_rules"
    const val RECENT_LOG = "recent_log"
    const val ABOUT = "about"
}

enum class DetectionMethod(val value: String) { ACCESSIBILITY("accessibility"), USAGE_STATS("usage_stats") }

enum class TileMechanism(val value: String) { SYSTEM_SETTING("system"), OVERLAY("overlay") }

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

    val tileCycleIncludesReverse: Boolean get() = settings.getString(PrefKeys.TILE_CYCLE, "apl") == "aplr"

    val tileMechanism: TileMechanism
        get() = enumPref(PrefKeys.TILE_MECHANISM, TileMechanism.SYSTEM_SETTING) { it.value }

    val overlayType: OverlayType
        get() = enumPref(PrefKeys.OVERLAY_TYPE, OverlayType.ACCESSIBILITY) { it.value }

    val ignoreLauncher: Boolean get() = settings.getBoolean(PrefKeys.IGNORE_LAUNCHER, true)

    val ignoredPackages: Set<String>
        get() = parsePackageList(settings.getString(PrefKeys.IGNORED_PACKAGES, "").orEmpty())

    val debounceMs: Long get() = longPref(PrefKeys.DEBOUNCE_MS, DEBOUNCE_DEFAULT, DEBOUNCE_RANGE)

    val pollIntervalMs: Long get() = longPref(PrefKeys.POLL_INTERVAL_MS, POLL_INTERVAL_DEFAULT, POLL_INTERVAL_RANGE)

    val quickActionsNotification: Boolean get() = settings.getBoolean(PrefKeys.QUICK_ACTIONS_NOTIFICATION, false)

    val startOnBoot: Boolean get() = settings.getBoolean(PrefKeys.START_ON_BOOT, false)

    /** Set by the tile service when the tile is added or removed; there is no API to query it. */
    var tileAdded: Boolean
        get() = state.getBoolean("tile_added", false)
        set(value) = state.edit { putBoolean("tile_added", value) }

    /** Mode chosen from the tile/notification when the tile uses the overlay mechanism. */
    var manualOverlayMode: OrientationMode
        get() = state.getString("manual_overlay_mode", null)
            ?.let { name -> OrientationMode.entries.firstOrNull { it.name == name } } ?: OrientationMode.AUTO
        set(value) = state.edit { putString("manual_overlay_mode", value.name) }

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
