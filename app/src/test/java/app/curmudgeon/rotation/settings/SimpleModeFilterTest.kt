// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleModeFilterTest {
    private class Node(val key: String?, val children: List<Node>? = null) {
        var shown: Boolean? = null
    }

    private fun apply(nodes: List<Node>, advanced: Boolean) =
        SimpleModeFilter.applyTree(nodes, { it.children }, { SimpleModeFilter.isShown(it.key, advanced) }) { node, shown -> node.shown = shown }

    @Test
    fun advancedShowsEverything() {
        val nodes = listOf(Node("category", listOf(Node(PrefKeys.DEBOUNCE_MS))), Node(PrefKeys.START_ON_BOOT))
        assertTrue(apply(nodes, advanced = true))
        assertEquals(true, nodes[0].shown)
        assertEquals(true, nodes[0].children!![0].shown)
        assertEquals(true, nodes[1].shown)
    }

    @Test
    fun simpleShowsOnlySimpleKeysAndCategoriesWithVisibleChildren() {
        val timing = Node("category_timing", listOf(Node(PrefKeys.DEBOUNCE_MS), Node(PrefKeys.POLL_INTERVAL_MS)))
        val detection = Node("category_detection", listOf(Node(PrefKeys.DETECTION_METHOD), Node(PrefKeys.IGNORE_LAUNCHER)))
        assertTrue(apply(listOf(detection, timing), advanced = false))
        assertEquals(true, detection.shown)
        assertEquals(true, detection.children!![0].shown)
        assertEquals(false, detection.children[1].shown)
        assertEquals(false, timing.shown)
        assertEquals(false, timing.children!![0].shown)
    }

    @Test
    fun nothingVisibleReportsFalse() {
        assertFalse(apply(listOf(Node(PrefKeys.OVERLAY_TYPE), Node("empty", emptyList())), advanced = false))
    }

    @Test
    fun sectionRowHiddenInSimpleModeOnlyWithoutSimpleKeys() {
        assertTrue(SimpleModeFilter.isSectionShown(listOf(PrefKeys.TILE_MECHANISM, PrefKeys.RESTORE_ON_LEAVE), advanced = false))
        assertFalse(SimpleModeFilter.isSectionShown(listOf(PrefKeys.TILE_MECHANISM, PrefKeys.OVERLAY_TYPE), advanced = false))
        assertTrue(SimpleModeFilter.isSectionShown(listOf(PrefKeys.TILE_MECHANISM), advanced = true))
    }

    @Test
    fun searchEntryMatchesTitleSummaryOrSection() {
        val entry = SettingsIndex.Entry("debounce_ms", "Debounce", "", "Timing", 0, "App detection")
        assertTrue(entry.matches("bounce"))
        assertTrue(entry.matches("DETECTION"))
        assertFalse(entry.matches("tile"))
        assertEquals("App detection › Timing", entry.location)
        assertEquals("Rules", entry.copy(category = null, sectionTitle = "Rules").location)
    }
}
