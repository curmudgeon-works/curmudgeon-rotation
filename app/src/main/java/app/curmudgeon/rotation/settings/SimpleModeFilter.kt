// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import androidx.preference.PreferenceGroup

/** Simple mode shows only the settings most people need; Advanced shows everything. */
object SimpleModeFilter {
    val SIMPLE_KEYS = setOf(
        PrefKeys.DETECTION_METHOD,
        PrefKeys.RESTORE_ON_LEAVE,
        PrefKeys.TILE_CYCLE,
        PrefKeys.EXPORT_RULES,
        PrefKeys.IMPORT_RULES,
        PrefKeys.ABOUT,
    )

    /**
     * Recursively sets visibility under [group]: every preference in advanced mode, only
     * [SIMPLE_KEYS] otherwise; a category is visible only if one of its children is.
     * Returns whether anything in [group] is visible.
     */
    fun apply(group: PreferenceGroup, advanced: Boolean): Boolean {
        var anyVisible = false
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            val visible = if (preference is PreferenceGroup) apply(preference, advanced) else advanced || preference.key in SIMPLE_KEYS
            preference.isVisible = visible
            anyVisible = anyVisible || visible
        }
        return anyVisible
    }
}
