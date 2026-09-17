// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.rules.RuleMatcher
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.settings.Prefs

/**
 * Pipeline from detected foreground activities to rules: filter transient windows, debounce,
 * log, match, apply. Both detection sources (accessibility events, usage-stats polling) feed it.
 * Main thread only.
 */
object ForegroundTracker {
    private const val SYSTEM_PACKAGES_TTL_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private val debouncer = Debouncer<WindowEvent>()
    private var filter: WindowFilter? = null
    private var filterBuiltAt = 0L

    /** The foreground activity rules were last evaluated for, or null when detection is stopped. */
    var current: WindowEvent? = null
        private set

    private val deliver = Runnable {
        debouncer.poll(SystemClock.uptimeMillis())?.let { event ->
            current = event
            RecentActivityLog.record(event, System.currentTimeMillis())
            evaluate(event)
        }
    }

    fun onWindowEvent(context: Context, event: WindowEvent) {
        if (!filter(context).accepts(event.packageName)) return
        handler.removeCallbacks(deliver)
        val dueAt = debouncer.submit(event, SystemClock.uptimeMillis(), Prefs.debounceMs) ?: return
        handler.postAtTime(deliver, dueAt)
    }

    /** Re-applies rules to the current app, e.g. after a rule was edited. */
    fun reevaluate() {
        current?.let(::evaluate)
    }

    /** Settings that shape the filter changed; rebuild it on the next event. */
    fun invalidateFilter() {
        filter = null
    }

    /** Detection stopped: forget the foreground app and end any rule-driven rotation. */
    fun stop() {
        handler.removeCallbacks(deliver)
        debouncer.reset()
        if (current != null) {
            current = null
            OrientationController.onForegroundChanged(null)
            OrientationController.applyRule(null)
        }
    }

    private fun evaluate(event: WindowEvent) {
        OrientationController.onForegroundChanged(event.packageName)
        OrientationController.applyRule(RuleMatcher.match(RuleStore.all(), event.packageName, event.activity))
    }

    private fun filter(context: Context): WindowFilter {
        val now = SystemClock.uptimeMillis()
        filter?.takeIf { now - filterBuiltAt < SYSTEM_PACKAGES_TTL_MS }?.let { return it }
        return WindowFilter(
            ownPackage = context.packageName,
            userIgnoredPackages = Prefs.ignoredPackages,
            inputMethodPackages = SystemPackages.inputMethods(context),
            launcherPackages = SystemPackages.launchers(context),
            ignoreLauncher = Prefs.ignoreLauncher,
        ).also {
            filter = it
            filterBuiltAt = now
        }
    }
}
