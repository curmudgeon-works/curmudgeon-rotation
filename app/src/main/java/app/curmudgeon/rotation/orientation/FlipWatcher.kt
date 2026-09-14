// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Listens to the orientation sensor until the phone is held in [target], then calls [onTurned] once on the main
 * thread. Without an orientation sensor it calls [onTurned] straight away.
 */
class SensorTurnWatcher(context: Context, target: Posture, private val onTurned: () -> Unit) : TurnWatcher {
    private val tracker = TurnTracker(target)
    private var done = false

    private val listener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
        override fun onOrientationChanged(orientation: Int) {
            if (!done && tracker.onSample(Posture.of(orientation), SystemClock.uptimeMillis())) finish()
        }
    }

    override fun start() {
        if (listener.canDetectOrientation()) listener.enable() else finish()
    }

    override fun stop() {
        done = true
        listener.disable()
    }

    private fun finish() {
        stop()
        onTurned()
    }
}

/**
 * Watches the built-in display until it shows landscape ([landscape] true) or portrait, then calls [onTurned] once
 * on the main thread. With [timeoutMs], calls [onTimeout] instead if that doesn't happen in time.
 *
 * Currently unused, kept on purpose. It powered "Flip now", a tile tap action removed 2026-09-14 after testing in
 * favour of the sensor flip: the same round trip, but auto-rotate came on as soon as the screen had turned (so it
 * only stayed if the phone was already held that way) and the start came back when the screen turned back. To bring
 * it back: add a TileAction that calls a flip variant using `DisplayTurnWatcher(context, toLandscape, 2000L,
 * onTimeout = restore start) { onFlipTurned(...) }` in place of [SensorTurnWatcher], and
 * `DisplayTurnWatcher(context, !toLandscape) { onFlipTurnedBack(...) }` for the second phase.
 */
class DisplayTurnWatcher(
    context: Context,
    private val landscape: Boolean,
    private val timeoutMs: Long? = null,
    private val onTimeout: () -> Unit = {},
    private val onTurned: () -> Unit,
) : TurnWatcher {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) check()
        }

        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private val timeout = Runnable {
        if (!done) {
            stop()
            onTimeout()
        }
    }

    override fun start() {
        displays.registerDisplayListener(listener, handler)
        timeoutMs?.let { handler.postDelayed(timeout, it) }
        check()
    }

    override fun stop() {
        done = true
        displays.unregisterDisplayListener(listener)
        handler.removeCallbacks(timeout)
    }

    private fun check() {
        if (done) return
        val rotation = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: return
        if ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) == landscape) {
            stop()
            onTurned()
        }
    }
}
