// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.curmudgeon.rotation.detect.AccessibilityDetectionService

/** Permission checks and the system screens where the user grants them. */
object Permissions {
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Whether the user switched the service on in accessibility settings (it may still be binding). */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, AccessibilityDetectionService::class.java)
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            .any { ComponentName.unflattenFromString(it) == ours }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun writeSettingsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** App info, where "Allow restricted settings" lives for sideloaded apps on Android 13+. */
    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Opens [intent]; some OEM builds lack per-app variants, so falls back to [fallback]. */
    fun open(context: Context, intent: Intent, fallback: Intent? = null): Boolean {
        for (candidate in listOfNotNull(intent, fallback)) {
            try {
                context.startActivity(candidate)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
