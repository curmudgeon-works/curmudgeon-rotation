// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation

import android.app.Application
import android.content.SharedPreferences
import app.curmudgeon.rotation.detect.ForegroundTracker
import app.curmudgeon.rotation.detect.RecentActivityLog
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.PrefKeys
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.tile.LandscapeLockTileService
import app.curmudgeon.rotation.tile.RotationTileService

/** Wires the singletons together; everything runs in this one process. */
class CurmudgeonApp : Application() {
    // held in a field: SharedPreferences keeps only weak references to listeners
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefKeys.IGNORE_LAUNCHER, PrefKeys.IGNORED_PACKAGES -> ForegroundTracker.invalidateFilter()
            PrefKeys.TILE_MECHANISM, PrefKeys.OVERLAY_TYPE -> OrientationController.refresh()
            PrefKeys.DETECTION_METHOD -> {
                ForegroundTracker.stop()
                RotationService.sync(this)
            }
            PrefKeys.QUICK_ACTIONS_NOTIFICATION -> RotationService.sync(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        RuleStore.init(Prefs.state)
        RecentActivityLog.init(Prefs.state)
        OrientationController.init(this)
        RotationService.createChannel(this)

        Prefs.settings.registerOnSharedPreferenceChangeListener(settingsListener)
        RuleStore.addListener { ForegroundTracker.reevaluate() }
        OrientationController.addListener {
            RotationTileService.requestUpdate(this)
            LandscapeLockTileService.requestUpdate(this)
            RotationService.sync(this) // a finished flip no longer needs the service
        }
    }
}
