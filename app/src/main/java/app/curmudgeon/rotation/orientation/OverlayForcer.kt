// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Owns one invisible window whose [WindowManager.LayoutParams.screenOrientation] asks the window
 * manager for an orientation. A visible window's orientation request is honoured even when the app
 * underneath locks its own orientation, which is what lets this override apps like video players.
 *
 * The window is 0x0, not focusable and not touchable, so it never intercepts input or draws.
 *
 * @param context a service context for TYPE_ACCESSIBILITY_OVERLAY (it needs the service's window
 * token), any context for TYPE_APPLICATION_OVERLAY.
 */
class OverlayForcer(private val context: Context, private val windowType: Int) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var orientation: Int? = null

    val isShowing: Boolean get() = view != null

    /** Requests [screenOrientation], or removes the window for null. Returns false if the window could not be shown. */
    fun set(screenOrientation: Int?): Boolean {
        if (screenOrientation == null) {
            remove()
            return true
        }
        if (screenOrientation == orientation && view != null) return true
        val params = WindowManager.LayoutParams(
            0, 0, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.screenOrientation = screenOrientation
            title = "CurmudgeonRotation"
        }
        return try {
            val existing = view
            if (existing == null) {
                val newView = View(context)
                windowManager.addView(newView, params)
                view = newView
            } else {
                windowManager.updateViewLayout(existing, params)
            }
            orientation = screenOrientation
            true
        } catch (e: RuntimeException) { // BadTokenException, SecurityException, IllegalStateException
            Log.w("OverlayForcer", "Could not show orientation window (type $windowType)", e)
            remove()
            false
        }
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeViewImmediate(it) } }
        view = null
        orientation = null
    }
}
