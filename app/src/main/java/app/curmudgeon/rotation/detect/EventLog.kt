// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import app.curmudgeon.rotation.settings.Prefs
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class LoggedEvent(val time: Long, val text: String)

/**
 * What the app did and why, newest first: tile taps, flips and Lock from start to end (the sensor turning them,
 * rotation changed elsewhere), per-app rules applying and ending. Never the apps opened when no rule acts. Only
 * while "Keep an event log" is on (off by default); every line also goes to logcat under [TAG]. On the device only,
 * in the app's private preferences; shown and cleared from the settings. Main thread only.
 */
object EventLog {
    const val TAG = "CurmudgeonRotation"
    private const val KEY = "event_log"
    private const val MAX_ENTRIES = 300
    private lateinit var prefs: SharedPreferences
    private var entries: List<LoggedEvent> = emptyList()

    fun init(statePrefs: SharedPreferences) {
        prefs = statePrefs
        entries = prefs.getString(KEY, null)?.let(::parse) ?: emptyList()
    }

    fun all(): List<LoggedEvent> = entries

    fun log(text: String) {
        if (!Prefs.keepEventLog) return
        Log.i(TAG, text)
        if (!::prefs.isInitialized) return
        entries = (listOf(LoggedEvent(System.currentTimeMillis(), text)) + entries).take(MAX_ENTRIES)
        save()
    }

    fun clear() {
        entries = emptyList()
        save()
    }

    private fun save() {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("t", it.time).put("m", it.text)) }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun parse(json: String): List<LoggedEvent>? = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LoggedEvent(o.getLong("t"), o.getString("m"))
        }
    } catch (_: JSONException) {
        null
    }
}
