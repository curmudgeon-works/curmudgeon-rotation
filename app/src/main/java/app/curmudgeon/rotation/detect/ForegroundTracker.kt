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
 * Only while something needs the foreground app ([isWanted]): a per-app rule, a flip that ends when its app is
 * left, or a tile or Rotate choice set to be lifted on the launcher. Otherwise events are dropped and the app in front
 * is forgotten; the accessibility service stops receiving them altogether ([sync]). Main thread only.
 */
object ForegroundTracker {
    private const val SYSTEM_PACKAGES_TTL_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private val debouncer = Debouncer<WindowEvent>()
    private var filter: WindowFilter? = null
    private var launchers: Set<String> = emptySet()
    private var filterBuiltAt = 0L

    /** The foreground activity rules were last evaluated for, or null when detection is stopped. */
    var current: WindowEvent? = null
        private set

    private val deliver = Runnable {
        debouncer.poll(SystemClock.uptimeMillis())?.let { event ->
            if (!isWanted()) return@let forget()
            current = event
            // only apps with a rule: the rule editor offers their screens; other apps are not written down
            if (RuleStore.find(event.packageName) != null) RecentActivityLog.record(event, System.currentTimeMillis())
            evaluate(event)
        }
    }

    /**
     * A per-app rule exists (and rules win), a flip is waiting and must hear when its app is left, or a tile or the
     * Rotate choice must be lifted while the launcher is in front.
     */
    fun isWanted(): Boolean =
        (Prefs.rulesWin && RuleStore.all().isNotEmpty()) || OrientationController.isFlipping || OrientationController.watchesLauncher

    /**
     * Rules or a flip changed: subscribes the accessibility service to app changes only while [isWanted], and forgets
     * the app in front when not (a later flip must not take a stale one for where it began).
     */
    fun sync() {
        val wanted = isWanted()
        if (!wanted) forget()
        AccessibilityDetectionService.listen(wanted)
    }

    fun onWindowEvent(context: Context, event: WindowEvent) {
        if (!isWanted()) return forget()
        if (!filter(context).accepts(event.packageName)) return
        handler.removeCallbacks(deliver)
        val dueAt = debouncer.submit(event, SystemClock.uptimeMillis(), Prefs.debounceMs) ?: return
        handler.postAtTime(deliver, dueAt)
    }

    /** "Per-app rules win" changed: off ends any rule in force now, on applies the current app's rule. */
    fun onRulesWinChanged() {
        if (!Prefs.rulesWin && current != null) OrientationController.applyRule(null) else reevaluate()
        sync()
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
            OrientationController.onForegroundChanged(null, false)
            OrientationController.applyRule(null)
        }
    }

    /** Nothing needs the foreground app: drop it without touching rotation (no rule can be active). */
    private fun forget() {
        handler.removeCallbacks(deliver)
        debouncer.reset()
        if (current != null) {
            current = null
            OrientationController.onForegroundChanged(null, false)
        }
    }

    /**
     * The launcher replaces the app like any other screen and ends its rule, unless "Ignore launcher" (per-app rules)
     * keeps the rule in force there.
     */
    private fun evaluate(event: WindowEvent) {
        val launcher = event.packageName in launchers
        OrientationController.onForegroundChanged(event.packageName, launcher)
        if (launcher && Prefs.ignoreLauncher && RuleStore.find(event.packageName) == null) return
        OrientationController.applyRule(
            if (Prefs.rulesWin) RuleMatcher.match(RuleStore.all(), event.packageName, event.activity) else null,
        )
    }

    private fun filter(context: Context): WindowFilter {
        val now = SystemClock.uptimeMillis()
        filter?.takeIf { now - filterBuiltAt < SYSTEM_PACKAGES_TTL_MS }?.let { return it }
        launchers = SystemPackages.launchers(context)
        return WindowFilter(Prefs.ignoredPackages).also {
            filter = it
            filterBuiltAt = now
        }
    }
}
