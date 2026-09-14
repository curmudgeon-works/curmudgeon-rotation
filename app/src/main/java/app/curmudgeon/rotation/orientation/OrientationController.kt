// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.Surface
import app.curmudgeon.rotation.rules.RuleAction
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.settings.TileMechanism
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Single place that changes rotation, combining two layers:
 * - manual: the tile / notification choice (system setting or overlay, per settings)
 * - rule: the action matched for the foreground app
 *
 * System-setting changes go through [SystemRotationSession] so the user's own values come back
 * when a rule ends. Overlay orientation is the rule's if it has one, else the manual overlay mode.
 * Main thread only.
 */
object OrientationController {
    private lateinit var appContext: Context
    private lateinit var systemSettings: SystemRotationSettings
    private lateinit var session: SystemRotationSession
    private var ruleOverlay: Int? = null
    private var flip: ActiveFlip? = null
    private var forcedFrom: FlipStart? = null
    private var forcedWritten: SystemRotation? = null
    private var forcedWatcher: TurnWatcher? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun init(context: Context) {
        appContext = context.applicationContext
        systemSettings = SystemRotationSettings(appContext)
        session = SystemRotationSession(systemSettings, Prefs.rotationStateStore)
        systemSettings.observe(::onSystemRotationChanged)
    }

    /**
     * Rotation changed, possibly outside this app (system Auto-rotate tile, rotate button, a rule). If it no longer
     * holds what a running flip or Lock wrote, that change is the user's new setting: the temporary state is dropped
     * (with its hard lock) and nothing is restored later.
     */
    private fun onSystemRotationChanged() {
        val written = flip?.written ?: forcedWritten ?: return
        if (!written.isStillIn(systemSettings.read())) abandonFlip()
    }

    /**
     * Mode shown on the tile and notification: the last manual choice while the system still matches it,
     * otherwise what the system setting says (someone changed rotation elsewhere).
     */
    fun currentMode(): OrientationMode {
        val system = OrientationMode.fromSystemRotation(systemSettings.read())
        val manual = Prefs.manualMode
        return if (ManualModeResolver.consistent(manual, system)) manual else system
    }

    /** The tile needs "Modify system settings"; the overlay hard lock is an optional extra. */
    fun isManualAvailable(): Boolean = systemSettings.canWrite()

    /** Whether Portrait / Landscape are hard locks (overlay window) rather than plain system locks apps can override. */
    fun isHardLockAvailable(): Boolean = Prefs.tileMechanism == TileMechanism.OVERLAY && OverlayHost.isAvailable(appContext)

    /**
     * Switches rotation on the user's explicit request (tile, long-press, notification): always the system
     * setting, plus the overlay window for Portrait / Landscape when the hard lock is enabled and available.
     * Returns false without "Modify system settings".
     */
    fun setManualMode(mode: OrientationMode): Boolean {
        if (!systemSettings.canWrite()) return false
        endFlip()
        session.applyManual(mode.toSystemRotation(systemSettings.read()))
        Prefs.manualMode = mode
        applyOverlay()
        notifyChanged()
        return true
    }

    /** Where a flip started from tile tap is, or null when none is running (see [flip]). */
    val flipPhase: FlipPhase? get() = flip?.phase

    /**
     * Tile flip, a round trip that ends where it started:
     * 1. locks the opposite of what the screen shows (never the hard lock);
     * 2. once the phone is held that way, turns auto-rotate on so the screen follows the phone (if auto-rotate was
     *    on to begin with, the flip is over here);
     * 3. once the phone is turned back, restores the starting settings (a portrait lock ends as a portrait lock).
     * No timeout: lying on your side the sensor never agrees, and holding the lock is then what you want.
     * Tapping during a flip or Lock heads back toward the start: if that is where the starting lock points, it
     * restores it; otherwise a new flip starts from the same start. A rotation change made elsewhere meanwhile ends
     * the flip without restoring (see [onSystemRotationChanged]). Returns false without "Modify system settings".
     *
     * A sensor-free variant ("Flip now") existed: [DisplayTurnWatcher] explains how to bring it back.
     */
    fun flip(): Boolean {
        if (!systemSettings.canWrite()) return false
        val toLandscape = !isDisplayLandscape()
        val start = temporaryStart() ?: FlipStart(systemSettings.read(), currentMode())
        val resuming = flip != null || forcedFrom != null
        endFlip()
        if (resuming && start.lockedLandscape() == toLandscape) {
            restore(start)
            return true
        }
        val mode = if (toLandscape) OrientationMode.LANDSCAPE else OrientationMode.PORTRAIT
        val locked = mode.toSystemRotation(start.rotation)
        // temporary: Prefs.manualMode is left alone, so nothing stale stays behind if the flip never finishes
        session.applyManual(locked)
        val active = ActiveFlip(start, locked)
        flip = active
        active.watch(SensorTurnWatcher(appContext, Posture.of(toLandscape)) { onFlipTurned(active, toLandscape) })
        applyOverlay()
        notifyChanged()
        return true
    }

    /** The start of a running flip or Lock whose settings are still in place, or null. */
    private fun temporaryStart(): FlipStart? {
        val current = systemSettings.read()
        return flip?.takeIf { it.written.isStillIn(current) }?.start
            ?: forcedFrom?.takeIf { forcedWritten?.isStillIn(current) == true }
    }

    /** The "Lock" tile is holding landscape (see [toggleForcedLandscape]). */
    val isLandscapeForced: Boolean get() = forcedFrom != null

    /** Whether a hard lock can be shown right now (accessibility service running, or "Display over other apps"). */
    fun canForce(): Boolean = OverlayHost.isAvailable(appContext)

    /**
     * "Lock" tile: the first tap locks landscape with the hard lock (either landscape side, regardless
     * of the tile mechanism setting), so apps that insist on portrait (HBO Max) turn too; the next tap puts the
     * settings from before back. Ends any running flip, keeping its start. Returns false when it can't: no
     * "Modify system settings" or no hard lock available.
     */
    fun toggleForcedLandscape(): Boolean {
        forcedFrom?.let { start ->
            if (forcedWritten?.isStillIn(systemSettings.read()) == true) restore(start) else abandonFlip()
            return true
        }
        if (!systemSettings.canWrite() || !canForce()) return false
        val start = temporaryStart() ?: FlipStart(systemSettings.read(), currentMode())
        endFlip()
        val locked = OrientationMode.LANDSCAPE.toSystemRotation(start.rotation)
        session.applyManual(locked)
        forcedFrom = start
        forcedWritten = locked
        if (Prefs.lockReleaseOnTurn) watchLockRelease()
        applyOverlay()
        notifyChanged()
        return true
    }

    /**
     * Optional Lock release ([Prefs.lockReleaseOnTurn]): waits for the phone to be held sideways, then for it to be
     * turned upright again, then puts the start back. Waiting for sideways first means tapping Lock while holding
     * the phone upright doesn't end it straight away.
     */
    private fun watchLockRelease() {
        val start = forcedFrom ?: return
        watchForLock(SensorTurnWatcher(appContext, Posture.LANDSCAPE) {
            if (forcedFrom === start) {
                watchForLock(SensorTurnWatcher(appContext, Posture.PORTRAIT) {
                    if (forcedFrom === start) if (forcedWritten?.isStillIn(systemSettings.read()) == true) restore(start) else abandonFlip()
                })
            }
        })
    }

    private fun watchForLock(next: TurnWatcher) {
        forcedWatcher?.stop()
        forcedWatcher = next
        next.start()
    }

    /** Whether the orientation sensor is in use (a flip, or a Lock set to release on turn); needs the process kept alive. */
    val isWatchingSensor: Boolean get() = flip != null || forcedWatcher != null

    private fun onFlipTurned(active: ActiveFlip, toLandscape: Boolean) {
        if (flip !== active) return
        when {
            !active.written.isStillIn(systemSettings.read()) -> abandonFlip()
            active.start.rotation.autoRotate -> restore(active.start)
            else -> {
                val auto = OrientationMode.AUTO.toSystemRotation(systemSettings.read())
                session.applyManual(auto)
                active.written = auto
                active.phase = FlipPhase.TURNED
                active.watch(SensorTurnWatcher(appContext, Posture.of(!toLandscape)) { onFlipTurnedBack(active) })
                notifyChanged()
            }
        }
    }

    private fun onFlipTurnedBack(active: ActiveFlip) {
        if (flip !== active) return
        if (active.written.isStillIn(systemSettings.read())) restore(active.start) else abandonFlip()
    }

    private fun ActiveFlip.watch(next: TurnWatcher) {
        watcher?.stop()
        watcher = next
        next.start()
    }

    private fun restore(start: FlipStart) {
        endFlip()
        session.applyManual(start.rotation)
        Prefs.manualMode = start.mode
        applyOverlay()
        notifyChanged()
    }

    private fun abandonFlip() {
        endFlip()
        applyOverlay()
        notifyChanged()
    }

    /** Ends a running flip or forced landscape without touching the settings. */
    private fun endFlip() {
        flip?.watcher?.stop()
        flip = null
        forcedWatcher?.stop()
        forcedWatcher = null
        forcedFrom = null
        forcedWritten = null
    }

    /** Whether the screen is showing landscape right now (whoever asked for it). */
    fun isDisplayLandscape(): Boolean = systemSettings.displayRotation().let { it == Surface.ROTATION_90 || it == Surface.ROTATION_270 }

    /** Applies the action matched for the foreground window; null ends any rule-driven rotation. */
    fun applyRule(action: RuleAction?) {
        val target = action?.systemTarget()
        if (target != null && systemSettings.canWrite()) session.applyRuleTarget(target) else session.end(Prefs.restoreOnLeave)
        ruleOverlay = action?.overlayOrientation()
        applyOverlay()
        notifyChanged()
    }

    /** Re-applies the overlay after settings that affect it changed (mechanism, overlay type). */
    fun refresh() {
        applyOverlay()
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun applyOverlay(): Boolean {
        val manual = when {
            forcedFrom != null -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // only a manual choice the settings still hold: a stale one must never hard-lock the screen
            Prefs.tileMechanism == TileMechanism.OVERLAY && flip == null ->
                Prefs.manualMode.takeIf { ManualModeResolver.consistent(it, OrientationMode.fromSystemRotation(systemSettings.read())) }
                    ?.toOverlayOrientation()
            else -> null
        }
        return OverlayHost.set(appContext, ruleOverlay ?: manual)
    }

    private fun notifyChanged() = listeners.forEach { it() }

    private fun RuleAction.systemTarget(): SystemRotation? = when (this) {
        RuleAction.AUTO_ROTATE_ON -> SystemRotation(true, systemSettings.read().userRotation)
        RuleAction.AUTO_ROTATE_OFF -> SystemRotation(false, systemSettings.displayRotation())
        RuleAction.PORTRAIT -> SystemRotation(false, Surface.ROTATION_0)
        RuleAction.LANDSCAPE -> SystemRotation(false, Surface.ROTATION_90)
        RuleAction.REVERSE_LANDSCAPE -> SystemRotation(false, Surface.ROTATION_270)
        else -> null
    }

    /**
     * Sensor variants where it helps: forced landscape still flips between both landscape sides,
     * forced auto rotates to all four sides even with the system rotation lock on.
     */
    private fun RuleAction.overlayOrientation(): Int? = when (this) {
        RuleAction.FORCE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        RuleAction.FORCE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        RuleAction.FORCE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        else -> null
    }
}
