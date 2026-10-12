// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation

import app.curmudgeon.rotation.detect.EventLog

import android.app.Application
import android.content.SharedPreferences
import app.curmudgeon.rotation.detect.ForegroundTracker
import app.curmudgeon.rotation.detect.RecentActivityLog
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.PrefKeys
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.tile.CustomTileStore
import app.curmudgeon.rotation.tile.LandscapeLockTileService
import app.curmudgeon.rotation.tile.PortraitLockTileService
import app.curmudgeon.rotation.tile.RotationTileService
import app.curmudgeon.rotation.tile.TilePresence

/** Wires the singletons together; everything runs in this one process. */
class CurmudgeonApp : Application() {
    // held in a field: SharedPreferences keeps only weak references to listeners
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefKeys.IGNORED_PACKAGES -> ForegroundTracker.invalidateFilter()
            PrefKeys.IGNORE_LAUNCHER -> ForegroundTracker.reevaluate()
            // refresh notifies the listener below, which syncs whether app changes are watched
            PrefKeys.IGNORE_LAUNCHER_TILES, PrefKeys.IGNORE_LAUNCHER_ROTATE -> OrientationController.refresh()
            PrefKeys.TILE_MECHANISM, PrefKeys.OVERLAY_TYPE -> OrientationController.refresh()
            PrefKeys.DETECTION_METHOD -> {
                ForegroundTracker.stop()
                ForegroundTracker.sync()
                RotationService.sync(this)
            }
            PrefKeys.QUICK_ACTIONS_NOTIFICATION -> RotationService.sync(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        RuleStore.init(Prefs.state)
        CustomTileStore.init(this, Prefs.state)
        TilePresence.restoreBuiltIns(this)
        RecentActivityLog.init(Prefs.state)
        EventLog.init(Prefs.state)
        OrientationController.init(this)
        RotationService.createChannel(this)

        Prefs.settings.registerOnSharedPreferenceChangeListener(settingsListener)
        RuleStore.addListener {
            ForegroundTracker.reevaluate() // first, so a deleted last rule ends before the app is forgotten
            ForegroundTracker.sync()
        }
        OrientationController.addListener {
            ForegroundTracker.sync() // a flip starting or ending, a tile or Rotate choice that watches the launcher
            RotationTileService.requestUpdate(this)
            LandscapeLockTileService.requestUpdate(this)
            PortraitLockTileService.requestUpdate(this)
            CustomTileStore.requestUpdates(this)
            RotationService.sync(this) // a finished flip no longer needs the service
        }
    }
}
