// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.XmlRes
import app.curmudgeon.rotation.R
import org.xmlpull.v1.XmlPullParser

/** Every setting's key, title and summary, read straight from the settings XML files, for search. */
object SettingsIndex {
    data class Entry(
        val key: String,
        val title: String,
        val summary: String,
        val category: String?,
        val sectionXml: Int,
        val sectionTitle: String,
    ) {
        fun matches(query: String) =
            title.contains(query, ignoreCase = true) || summary.contains(query, ignoreCase = true) || sectionTitle.contains(query, ignoreCase = true)

        /** "Section › Category", or just the section when the setting is not in a category. */
        val location: String get() = listOfNotNull(sectionTitle, category).joinToString(" › ")
    }

    private val simpleCache = mutableMapOf<Int, Boolean>()

    fun build(context: Context): List<Entry> {
        val out = mutableListOf<Entry>()
        read(context, R.xml.prefs_root, context.getString(R.string.settings_home_title), out, skipSectionRows = true)
        SettingsActivity.SECTIONS.values.forEach { section -> read(context, section.xml, context.getString(section.title), out, skipSectionRows = false) }
        return out
    }

    /** Whether any setting on [xml] is shown in simple mode (otherwise its home row is hidden in simple mode). */
    fun hasSimple(context: Context, @XmlRes xml: Int): Boolean = simpleCache.getOrPut(xml) {
        val entries = mutableListOf<Entry>()
        read(context, xml, "", entries, skipSectionRows = false)
        SimpleModeFilter.isSectionShown(entries.map { it.key }, advanced = false)
    }

    private fun read(context: Context, @XmlRes xml: Int, sectionTitle: String, out: MutableList<Entry>, skipSectionRows: Boolean) {
        val parser = context.resources.getXml(xml)
        var category: String? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                val title = attr(context, parser, "title") ?: continue
                if (parser.name == "PreferenceCategory") {
                    category = title
                    continue
                }
                val key = attr(context, parser, "key") ?: continue
                if (skipSectionRows && key in SettingsActivity.SECTIONS) continue // home rows lead to sections; search the sections instead
                val summary = attr(context, parser, "summary")?.replace("%s", "")?.trim().orEmpty()
                out.add(Entry(key, title, summary, category, xml, sectionTitle))
            }
        } finally {
            parser.close()
        }
    }

    /** Attribute by local name in any namespace (the settings XMLs use app:, android: works too); string references are resolved. */
    private fun attr(context: Context, parser: XmlResourceParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) != name) continue
            val res = parser.getAttributeResourceValue(i, 0)
            return if (res != 0) context.getString(res) else parser.getAttributeValue(i)
        }
        return null
    }
}
