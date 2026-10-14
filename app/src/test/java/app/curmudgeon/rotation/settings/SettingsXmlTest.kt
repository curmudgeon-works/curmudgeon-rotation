// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Checks the split settings screens against [PrefKeys] and [SimpleModeFilter] by reading the XML sources. */
class SettingsXmlTest {
    private val xmlDir = listOf(File("src/main/res/xml"), File("app/src/main/res/xml")).first { it.isDirectory }

    private fun keys(name: String): List<String> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(xmlDir, "$name.xml"))
        val all = doc.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
            .filter { it.tagName != "PreferenceCategory" && it.tagName != "PreferenceScreen" }
            .map { it.getAttributeNS(RES_AUTO, "key") }
    }

    private val sections = mapOf(
        "section_detection" to "prefs_detection",
        "section_rotation" to "prefs_rotation",
        "section_tile" to "prefs_tile",
        "section_rules" to "prefs_rules",
    )

    private val screens get() = sections.values

    @Test
    fun homeListsEverySectionThenAboutThenRateShare() {
        assertEquals(sections.keys.toList() + PrefKeys.ABOUT + PrefKeys.RATE_SHARE, keys("prefs_root"))
    }

    @Test
    fun everySettingLivesOnExactlyOneSectionScreen() {
        val placed = screens.flatMap { keys(it) }
        val expected = PrefKeys::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String } - PrefKeys.ADVANCED_MODE - PrefKeys.ABOUT - PrefKeys.RATE_SHARE - PrefKeys.CUSTOM_TILE_PREFIX
        assertEquals(placed.size, placed.toSet().size)
        assertEquals(expected.toSet(), placed.toSet())
    }

    @Test
    fun everySectionHasASimpleSetting() {
        screens.forEach { assertTrue(it, SimpleModeFilter.isSectionShown(keys(it), advanced = false)) }
        assertTrue(SimpleModeFilter.SIMPLE_KEYS.all { key -> key == PrefKeys.ABOUT || key == PrefKeys.RATE_SHARE || screens.any { key in keys(it) } })
    }

    private companion object {
        const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
    }
}
