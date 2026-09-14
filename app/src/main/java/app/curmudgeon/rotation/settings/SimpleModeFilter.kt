// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import androidx.preference.Preference
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

    /** Whether a single setting shows: every setting in advanced mode, only [SIMPLE_KEYS] otherwise. */
    fun isShown(key: String?, advanced: Boolean): Boolean = advanced || key in SIMPLE_KEYS

    /** Whether the home row of a section holding [sectionKeys] shows: hidden in simple mode when none of them is simple. */
    fun isSectionShown(sectionKeys: Collection<String>, advanced: Boolean): Boolean = advanced || sectionKeys.any { it in SIMPLE_KEYS }

    /**
     * Sets visibility over a tree: a leaf shows when [leafShown] says so, a group (a node with
     * non-null [children]) shows only if one of its children does. Returns whether anything in [nodes] shows.
     */
    fun <N> applyTree(nodes: List<N>, children: (N) -> List<N>?, leafShown: (N) -> Boolean, setShown: (N, Boolean) -> Unit): Boolean {
        var anyShown = false
        for (node in nodes) {
            val kids = children(node)
            val shown = if (kids != null) applyTree(kids, children, leafShown, setShown) else leafShown(node)
            setShown(node, shown)
            anyShown = anyShown || shown
        }
        return anyShown
    }

    /** [applyTree] over the preferences under [group]. */
    fun apply(group: PreferenceGroup, leafShown: (Preference) -> Boolean): Boolean =
        applyTree(group.childList(), { (it as? PreferenceGroup)?.childList() }, leafShown) { preference, shown -> preference.isVisible = shown }

    private fun PreferenceGroup.childList(): List<Preference> = (0 until preferenceCount).map(::getPreference)
}
