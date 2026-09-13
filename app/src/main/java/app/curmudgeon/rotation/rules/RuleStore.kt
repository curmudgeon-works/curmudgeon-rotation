// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import org.json.JSONException
import java.util.concurrent.CopyOnWriteArraySet

/** Rules persisted as JSON in the app's private SharedPreferences. Main thread only. */
object RuleStore {
    private const val KEY = "rules_json"
    private const val TAG = "RuleStore"

    private lateinit var prefs: SharedPreferences
    private var cache: List<AppRule> = emptyList()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun init(statePrefs: SharedPreferences) {
        prefs = statePrefs
        cache = prefs.getString(KEY, null)?.let { json ->
            try {
                RuleJson.fromJson(json)
            } catch (e: JSONException) {
                Log.e(TAG, "Stored rules unreadable, starting empty", e)
                null
            }
        } ?: emptyList()
    }

    fun all(): List<AppRule> = cache

    fun find(packageName: String): AppRule? = cache.firstOrNull { it.packageName == packageName }

    fun put(rule: AppRule) = save(cache.filterNot { it.packageName == rule.packageName } + rule)

    fun remove(packageName: String) = save(cache.filterNot { it.packageName == packageName })

    /** Adds [imported] rules; an imported rule replaces an existing rule for the same app. */
    fun merge(imported: Collection<AppRule>) {
        val importedPackages = imported.map { it.packageName }.toSet()
        save(cache.filterNot { it.packageName in importedPackages } + imported)
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun save(rules: List<AppRule>) {
        cache = rules.sortedBy { it.packageName }
        prefs.edit { putString(KEY, RuleJson.toJson(cache)) }
        listeners.forEach { it() }
    }
}
