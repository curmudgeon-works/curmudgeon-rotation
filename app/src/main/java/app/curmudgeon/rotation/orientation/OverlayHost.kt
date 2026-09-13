// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.WindowManager
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.settings.OverlayType
import app.curmudgeon.rotation.settings.Prefs

/**
 * Puts the requested overlay orientation on one of two windows:
 * - TYPE_ACCESSIBILITY_OVERLAY, owned by the running accessibility service. Needs no extra
 *   permission and is not hidden by apps that call setHideOverlayWindows (Android 12+).
 * - TYPE_APPLICATION_OVERLAY, needs "Display over other apps". Apps may hide it on Android 12+,
 *   and our process must be kept alive (foreground service) for it to persist.
 *
 * The preferred type comes from settings; the other one is used when the preferred one is not
 * available. Main thread only.
 */
object OverlayHost {
    private var accessibilityForcer: OverlayForcer? = null
    private var appForcer: OverlayForcer? = null
    private var requested: Int? = null

    /** True while the orientation is held by an app overlay window, which needs a live process. */
    val isUsingAppOverlay: Boolean get() = appForcer?.isShowing == true

    fun attach(service: AccessibilityService) {
        accessibilityForcer = OverlayForcer(service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        set(service.applicationContext, requested)
    }

    fun detach(context: Context) {
        accessibilityForcer?.remove()
        accessibilityForcer = null
        set(context.applicationContext, requested)
    }

    fun isAvailable(context: Context): Boolean = accessibilityForcer != null || Permissions.canDrawOverlays(context)

    /** Requests [screenOrientation] (null removes all windows). Returns false if no window could carry it. */
    fun set(context: Context, screenOrientation: Int?): Boolean {
        requested = screenOrientation
        val a11y = accessibilityForcer
        val app = if (Permissions.canDrawOverlays(context)) {
            appForcer ?: OverlayForcer(context.applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                .also { appForcer = it }
        } else {
            appForcer?.remove()
            null
        }
        if (screenOrientation == null) {
            a11y?.remove()
            app?.remove()
            return true
        }
        val candidates = if (Prefs.overlayType == OverlayType.ACCESSIBILITY) listOfNotNull(a11y, app) else listOfNotNull(app, a11y)
        val winner = candidates.firstOrNull { it.set(screenOrientation) }
        candidates.filter { it !== winner }.forEach { it.remove() }
        return winner != null
    }
}
