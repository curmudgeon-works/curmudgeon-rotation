// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager

/** Packages that are transient on screen: launchers and keyboards. */
object SystemPackages {
    fun launchers(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
    }

    fun inputMethods(context: Context): Set<String> =
        context.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
}
