// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.detect.AccessibilityDetectionService
import app.curmudgeon.rotation.detect.ForegroundTracker
import app.curmudgeon.rotation.detect.UsageStatsPoller
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.orientation.OverlayHost
import app.curmudgeon.rotation.settings.DetectionMethod
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.ui.MainActivity
import app.curmudgeon.rotation.ui.labelRes

/**
 * Foreground service that runs only while one of these is enabled:
 * - usage-stats detection (polling needs a live process),
 * - the quick-actions notification,
 * - an app-overlay orientation window without the accessibility service keeping the process alive.
 *
 * Call [sync] whenever one of those conditions may have changed; it starts or stops the service.
 */
class RotationService : Service() {
    private var poller: UsageStatsPoller? = null
    private val onRotationChanged: () -> Unit = { updateNotification() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        OrientationController.addListener(onRotationChanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // every startForegroundService() must be answered with startForeground()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        intent?.getStringExtra(EXTRA_MODE)?.let { name ->
            OrientationMode.entries.firstOrNull { it.name == name }?.let(OrientationController::setManualMode)
        }
        if (!isNeeded(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        updatePoller()
        return START_STICKY
    }

    override fun onDestroy() {
        OrientationController.removeListener(onRotationChanged)
        stopPoller()
        isRunning = false
        super.onDestroy()
    }

    private fun updatePoller() {
        if (usageStatsDetectionWanted(this)) {
            if (poller == null) {
                poller = UsageStatsPoller(this, { Prefs.pollIntervalMs }) { ForegroundTracker.onWindowEvent(this, it) }
                    .also { it.start() }
            }
        } else {
            stopPoller()
        }
    }

    private fun stopPoller() {
        poller?.let {
            it.stop()
            poller = null
            if (Prefs.detectionMethod == DetectionMethod.USAGE_STATS) ForegroundTracker.stop()
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val mode = OrientationController.currentMode()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = buildList {
            add(getString(R.string.notification_mode, getString(mode.labelRes())))
            if (usageStatsDetectionWanted(this@RotationService)) add(getString(R.string.notification_reason_usage))
            if (needsOverlayKeepAlive()) add(getString(R.string.notification_reason_overlay))
            if (needsFlipKeepAlive()) add(getString(R.string.notification_reason_flip))
        }.joinToString(" · ")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rotation_auto)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (Prefs.quickActionsNotification) {
                    addModeAction(OrientationMode.AUTO)
                    addModeAction(OrientationMode.PORTRAIT)
                    addModeAction(OrientationMode.LANDSCAPE)
                    addModeAction(OrientationMode.OFF)
                }
            }
            .build()
    }

    private fun NotificationCompat.Builder.addModeAction(mode: OrientationMode) {
        val intent = Intent(this@RotationService, RotationService::class.java).putExtra(EXTRA_MODE, mode.name)
        val pending = PendingIntent.getService(
            this@RotationService, mode.ordinal + 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        addAction(0, getString(mode.labelRes()), pending)
    }

    companion object {
        private const val TAG = "RotationService"
        private const val CHANNEL_ID = "rotation"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_MODE = "mode"

        @Volatile
        var isRunning = false
            private set

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun usageStatsDetectionWanted(context: Context): Boolean =
            Prefs.detectionMethod == DetectionMethod.USAGE_STATS && Permissions.hasUsageAccess(context)

        /** App overlay windows vanish with the process unless something keeps it alive. */
        private fun needsOverlayKeepAlive(): Boolean =
            OverlayHost.isUsingAppOverlay && !AccessibilityDetectionService.isConnected

        /** The orientation sensor stops with the process too; the accessibility service keeps it alive when enabled. */
        private fun needsFlipKeepAlive(): Boolean =
            OrientationController.isWatchingSensor && !AccessibilityDetectionService.isConnected

        fun isNeeded(context: Context): Boolean =
            usageStatsDetectionWanted(context) || Prefs.quickActionsNotification || needsOverlayKeepAlive() || needsFlipKeepAlive()

        /**
         * Starts, reconfigures or stops the service. Starting can be refused while the app is in the
         * background on Android 12+; it is retried on the next sync (app opened, tile tapped, boot).
         */
        fun sync(context: Context) {
            val needed = isNeeded(context)
            if (!needed && !isRunning) return
            if (!needed) {
                context.stopService(Intent(context, RotationService::class.java))
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, RotationService::class.java))
            } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException on API 31+
                Log.w(TAG, "Foreground service start not allowed now", e)
            }
        }
    }
}
