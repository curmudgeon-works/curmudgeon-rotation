// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object SystemPackages {
    /**
     * Launcher packages (home screen, app drawer, recents). Android Settings also answers the home intent, with a
     * negative priority, as a fallback for when no launcher can start; that one is not a launcher.
     */
    fun launchers(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.priority >= 0 }
            .map { it.activityInfo.packageName }.toSet()
    }
}
