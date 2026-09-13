// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Serializes rules for preferences and for export/import files (same format for both). */
object RuleJson {
    private const val FORMAT = "curmudgeon-rotation-rules"
    private const val VERSION = 1

    fun toJson(rules: Collection<AppRule>): String {
        val array = JSONArray()
        rules.sortedBy { it.packageName }.forEach { rule ->
            array.put(
                JSONObject()
                    .put("package", rule.packageName)
                    .put("action", rule.action.id)
                    .put("activities", JSONArray(rule.activities.sorted()))
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("rules", array)
            .toString(2)
    }

    /**
     * Parses [text]. Entries with an unknown action or missing package are skipped so files from a
     * newer version still import what they can; a later duplicate package replaces an earlier one.
     *
     * @throws JSONException if [text] is not a rules file at all.
     */
    fun fromJson(text: String): List<AppRule> {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) throw JSONException("Not a Curmudgeon Rotation rules file")
        val array = root.getJSONArray("rules")
        val byPackage = LinkedHashMap<String, AppRule>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val packageName = entry.optString("package").trim()
            val action = RuleAction.fromId(entry.optString("action")) ?: continue
            if (packageName.isEmpty()) continue
            val activities = entry.optJSONArray("activities")?.let { list ->
                (0 until list.length()).map { list.optString(it).trim() }.filter { it.isNotEmpty() }.toSet()
            } ?: emptySet()
            byPackage[packageName] = AppRule(packageName, action, activities)
        }
        return byPackage.values.toList()
    }
}
