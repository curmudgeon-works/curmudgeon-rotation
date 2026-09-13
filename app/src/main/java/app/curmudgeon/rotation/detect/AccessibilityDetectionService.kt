// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OverlayHost
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.DetectionMethod
import app.curmudgeon.rotation.settings.Prefs

/**
 * Opt-in accessibility service. Reads only the package and class name of windows that come to the
 * front (TYPE_WINDOW_STATE_CHANGED); it cannot see window content (canRetrieveWindowContent=false).
 * It also hosts the accessibility overlay window used by the "force" rule actions.
 */
class AccessibilityDetectionService : AccessibilityService() {
    private lateinit var resolver: ActivityResolver

    override fun onServiceConnected() {
        resolver = ActivityResolver(packageManager)
        isConnected = true
        OverlayHost.attach(this)
        OrientationController.refresh()
        RotationService.sync(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (Prefs.detectionMethod != DetectionMethod.ACCESSIBILITY) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        if (!resolver.isActivity(packageName, className)) return
        ForegroundTracker.onWindowEvent(this, WindowEvent(packageName, className))
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (!isConnected) return
        isConnected = false
        if (Prefs.detectionMethod == DetectionMethod.ACCESSIBILITY) ForegroundTracker.stop()
        OverlayHost.detach(this)
        OrientationController.refresh()
    }

    companion object {
        /** Whether the system has bound the service in this process. */
        @Volatile
        var isConnected = false
            private set
    }
}
