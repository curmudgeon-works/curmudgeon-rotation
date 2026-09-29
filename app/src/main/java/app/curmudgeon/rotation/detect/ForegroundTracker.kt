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
 * Only while something needs the foreground app ([isWanted]): a per-app rule, or a flip that ends when its app is
 * left. Otherwise events are dropped and the app in front is forgotten; the accessibility service stops receiving
 * them altogether ([sync]). Main thread only.
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
            if (!isWanted()) return@let forget()
            current = event
            // only apps with a rule: the rule editor offers their screens; other apps are not written down
            if (RuleStore.find(event.packageName) != null) RecentActivityLog.record(event, System.currentTimeMillis())
            evaluate(event)
        }
    }

    /** A per-app rule exists, or a flip is waiting and must hear when its app is left. */
    fun isWanted(): Boolean = RuleStore.all().isNotEmpty() || OrientationController.isFlipping

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

    /** Nothing needs the foreground app: drop it without touching rotation (no rule can be active). */
    private fun forget() {
        handler.removeCallbacks(deliver)
        debouncer.reset()
        if (current != null) {
            current = null
            OrientationController.onForegroundChanged(null)
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
