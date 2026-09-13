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
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun init(context: Context) {
        appContext = context.applicationContext
        systemSettings = SystemRotationSettings(appContext)
        session = SystemRotationSession(systemSettings, Prefs.rotationStateStore)
    }

    /** Mode shown on the tile and notification for the configured tile mechanism. */
    fun currentMode(): OrientationMode = when (Prefs.tileMechanism) {
        TileMechanism.SYSTEM_SETTING -> OrientationMode.fromSystemRotation(systemSettings.read())
        TileMechanism.OVERLAY -> Prefs.manualOverlayMode
    }

    /** Whether the tile mechanism has what it needs (permission or a running overlay host). */
    fun isManualAvailable(): Boolean = when (Prefs.tileMechanism) {
        TileMechanism.SYSTEM_SETTING -> systemSettings.canWrite()
        TileMechanism.OVERLAY -> OverlayHost.isAvailable(appContext)
    }

    /** Switches rotation on the user's explicit request. Returns false if the mechanism is unavailable. */
    fun setManualMode(mode: OrientationMode): Boolean {
        val ok = when (Prefs.tileMechanism) {
            TileMechanism.SYSTEM_SETTING -> systemSettings.canWrite().also { canWrite ->
                if (canWrite) session.applyManual(mode.toSystemRotation(systemSettings.read()))
            }
            TileMechanism.OVERLAY -> {
                Prefs.manualOverlayMode = mode
                applyOverlay() || mode == OrientationMode.AUTO
            }
        }
        notifyChanged()
        return ok
    }

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
        val manual = if (Prefs.tileMechanism == TileMechanism.OVERLAY) Prefs.manualOverlayMode.toOverlayOrientation() else null
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
