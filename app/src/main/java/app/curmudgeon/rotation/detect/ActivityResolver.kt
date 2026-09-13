// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build

/** Tells real activities apart from dialogs, popups and toasts, which also fire window events. Cached. */
class ActivityResolver(private val packageManager: PackageManager) {
    private val cache = HashMap<String, Boolean>()

    fun isActivity(packageName: String, className: String): Boolean {
        val key = "$packageName/$className"
        cache[key]?.let { return it }
        if (cache.size > MAX_CACHE) cache.clear()
        return resolve(packageName, className).also { cache[key] = it }
    }

    private fun resolve(packageName: String, className: String): Boolean = try {
        val component = ComponentName(packageName, className)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getActivityInfo(component, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        !isPackageVisible(packageName) && !WindowFilter.looksLikeFrameworkWindow(className)
    }

    private fun isPackageVisible(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val MAX_CACHE = 500
    }
}
