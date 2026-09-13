// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Alternative to the accessibility service: polls UsageStatsManager for the most recently resumed
 * activity, only while the screen is on. Slower to react (up to one interval) and wakes the CPU
 * every interval, which is why it's the advanced option.
 */
class UsageStatsPoller(
    private val context: Context,
    private val intervalMs: () -> Long,
    private val onEvent: (WindowEvent) -> Unit,
) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var lastEventTime = 0L
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, intervalMs())
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            if (intent.action == Intent.ACTION_SCREEN_ON) handler.post(tick)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastEventTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        ContextCompat.registerReceiver(context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (context.getSystemService(PowerManager::class.java).isInteractive) handler.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        context.unregisterReceiver(screenReceiver)
    }

    /**
     * Queries with an overlap because events can be recorded slightly after their timestamp; events
     * at or before the last one seen are skipped.
     */
    private fun poll() {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(lastEventTime - OVERLAP_MS, now) ?: return
        val event = UsageEvents.Event()
        var latest: WindowEvent? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != RESUMED || event.timeStamp <= lastEventTime) continue
            val className = event.className ?: continue
            lastEventTime = event.timeStamp
            latest = WindowEvent(event.packageName, className)
        }
        latest?.let(onEvent)
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 60 * 60 * 1000L
        const val OVERLAP_MS = 2000L

        val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }
}
