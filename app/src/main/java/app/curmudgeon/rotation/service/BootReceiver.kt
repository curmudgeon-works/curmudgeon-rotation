// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.curmudgeon.rotation.settings.Prefs

/** Starts usage-stats detection / the quick-actions notification after boot, if the user asked for it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.startOnBoot) RotationService.sync(context)
    }
}
