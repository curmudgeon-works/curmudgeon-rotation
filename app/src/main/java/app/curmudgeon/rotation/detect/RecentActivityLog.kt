// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class RecentActivity(val packageName: String, val activity: String, val lastSeen: Long)

/**
 * Screens recently brought to the front, newest first, one entry per activity. Feeds the rule
 * editor's "only these screens" suggestions and the log in settings. Stays on the device, in the
 * app's private preferences. Main thread only.
 */
object RecentActivityLog {
    private const val KEY = "recent_activities"
    private const val MAX_ENTRIES = 200

    private lateinit var prefs: SharedPreferences
    private var entries: List<RecentActivity> = emptyList()

    fun init(statePrefs: SharedPreferences) {
        prefs = statePrefs
        entries = prefs.getString(KEY, null)?.let(::parse) ?: emptyList()
    }

    fun all(): List<RecentActivity> = entries

    fun forPackage(packageName: String): List<RecentActivity> = entries.filter { it.packageName == packageName }

    fun record(event: WindowEvent, now: Long) {
        val entry = RecentActivity(event.packageName, event.activity, now)
        entries = (listOf(entry) + entries.filterNot { it.packageName == event.packageName && it.activity == event.activity })
            .take(MAX_ENTRIES)
        save()
    }

    fun clear() {
        entries = emptyList()
        save()
    }

    private fun save() {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("p", it.packageName).put("a", it.activity).put("t", it.lastSeen)) }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun parse(json: String): List<RecentActivity>? = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RecentActivity(o.getString("p"), o.getString("a"), o.getLong("t"))
        }
    } catch (_: JSONException) {
        null
    }
}
