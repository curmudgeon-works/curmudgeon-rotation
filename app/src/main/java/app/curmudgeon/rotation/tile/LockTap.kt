// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.content.Context
import android.content.Intent
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.settings.LockTapAction
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic

/**
 * Performs the Lock action (the Lock tile's tap, the Rotate tile's double-tap). Returns the setup screen to open
 * instead when a permission is missing, or null when done.
 */
fun LockTapAction.perform(context: Context): Intent? {
    val done = when (this) {
        LockTapAction.LANDSCAPE -> OrientationController.toggleForcedLandscape()
        LockTapAction.CYCLE -> OrientationController.cycleForcedLock()
    }
    if (done) return null
    val topic = if (!Permissions.canWriteSettings(context)) SetupTopic.WRITE_SETTINGS else SetupTopic.ACCESSIBILITY
    return SetupActivity.intent(context, topic)
}
