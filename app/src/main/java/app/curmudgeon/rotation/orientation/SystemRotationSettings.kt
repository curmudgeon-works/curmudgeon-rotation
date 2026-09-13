// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Surface

/** Reads and writes rotation in Settings.System. Writes are silently skipped without WRITE_SETTINGS. */
class SystemRotationSettings(private val context: Context) : SystemRotationAccess {
    private val resolver = context.contentResolver

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    override fun read() = SystemRotation(
        autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
        userRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0),
    )

    override fun write(value: SystemRotation) {
        if (!canWrite()) return
        try {
            // user rotation first, so turning auto-rotate off lands directly on the target rotation
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, value.userRotation)
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (value.autoRotate) 1 else 0)
        } catch (e: SecurityException) {
            Log.w("SystemRotation", "WRITE_SETTINGS revoked while writing", e)
        }
    }

    /** Current rotation of the built-in display, as a Surface.ROTATION_* value. */
    fun displayRotation(): Int =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
}
