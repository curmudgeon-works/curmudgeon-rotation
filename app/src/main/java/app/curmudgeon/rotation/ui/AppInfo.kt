// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class AppInfo(val packageName: String, val label: String, val icon: Drawable?, val isSystem: Boolean, val installed: Boolean)

object AppInfoLoader {
    /** Label and icon for one package; falls back to the package name for apps that are gone. */
    fun load(context: Context, packageName: String): AppInfo {
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            AppInfo(packageName, info.loadLabel(pm).toString(), info.loadIcon(pm), info.isSystemApp(), installed = true)
        } catch (_: PackageManager.NameNotFoundException) {
            AppInfo(packageName, packageName, null, isSystem = false, installed = false)
        }
    }

    /** All apps with a launcher entry, sorted by label. Slow (loads icons): call off the main thread. */
    fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm), it.isSystemApp(), installed = true) }
            .sortedBy { it.label.lowercase() }
    }

    /** Preinstalled and never updated. Updated system apps (YouTube, Chrome on many phones) count as user apps. */
    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
}
