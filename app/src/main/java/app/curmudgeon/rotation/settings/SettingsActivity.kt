// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivitySettingsBinding
import app.curmudgeon.rotation.ui.setUpEdgeToEdge

/**
 * Settings, structured like Curmudgeon Keyboard and Browser: a home list of sections, one screen per section,
 * and a top bar with back, title, the Simple / Advanced switch and search (search always covers every setting).
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val index by lazy { SettingsIndex.build(this) }

    /** One settings screen: [xml] with [title] in the top bar. */
    data class Section(@param:XmlRes val xml: Int, @param:StringRes val title: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpEdgeToEdge(binding.root)

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.advancedSwitch.isChecked = Prefs.advancedMode
        binding.advancedSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.advancedMode = checked
            currentScreen()?.applyMode()
        }
        binding.searchButton.setOnClickListener { if (binding.searchField.isVisible) closeSearch() else openSearch() }
        binding.searchField.doAfterTextChanged { showResults(it?.toString().orEmpty()) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.searchField.isVisible -> closeSearch()
                    supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null) {
            show(R.xml.prefs_root, R.string.settings_home_title, addToBackStack = false)
        } else if (savedInstanceState.getBoolean(STATE_SEARCHING)) {
            // the field's text is restored after onCreate, which re-runs the search through the text listener
            binding.title.isVisible = false
            binding.searchField.isVisible = true
        }
        supportFragmentManager.addOnBackStackChangedListener { updateTitle() }
        updateTitle()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SEARCHING, binding.searchField.isVisible)
    }

    fun show(@XmlRes xml: Int, @StringRes title: Int, addToBackStack: Boolean = true, focusKey: String? = null) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment.newInstance(xml, title, focusKey))
        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
        binding.title.setText(title)
    }

    private fun currentScreen() = supportFragmentManager.findFragmentById(R.id.settingsContainer) as? SettingsFragment

    private fun updateTitle() {
        binding.title.setText(currentScreen()?.titleRes ?: R.string.settings_home_title)
    }

    // --- search ---

    private fun openSearch() {
        binding.title.isVisible = false
        binding.searchField.isVisible = true
        binding.searchField.setText("")
        binding.searchField.requestFocus()
        WindowInsetsControllerCompat(window, binding.searchField).show(WindowInsetsCompat.Type.ime())
    }

    private fun closeSearch() {
        WindowInsetsControllerCompat(window, binding.searchField).hide(WindowInsetsCompat.Type.ime())
        binding.searchField.isVisible = false
        binding.title.isVisible = true
        binding.searchResults.isVisible = false
        binding.settingsContainer.isVisible = true
    }

    private fun showResults(query: String) {
        val q = query.trim()
        if (q.isEmpty() || !binding.searchField.isVisible) {
            binding.searchResults.isVisible = false
            binding.settingsContainer.isVisible = true
            return
        }
        val hits = index.filter { it.matches(q) }
        binding.searchResults.adapter = object : ArrayAdapter<SettingsIndex.Entry>(this, R.layout.item_search_result, R.id.resultTitle, hits) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val entry = hits[position]
                view.findViewById<TextView>(R.id.resultTitle).text = entry.title
                view.findViewById<TextView>(R.id.resultSubtitle).text = entry.location
                return view
            }
        }
        binding.searchResults.setOnItemClickListener { _, _, position, _ -> openResult(hits[position]) }
        binding.settingsContainer.isVisible = false
        binding.searchResults.isVisible = true
    }

    /** Closes search and opens the result's screen on top of home (back returns home), scrolled to the setting. */
    private fun openResult(entry: SettingsIndex.Entry) {
        closeSearch()
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val section = SECTIONS.values.firstOrNull { it.xml == entry.sectionXml }
        if (section == null) {
            show(R.xml.prefs_root, R.string.settings_home_title, addToBackStack = false, focusKey = entry.key)
        } else {
            show(section.xml, section.title, focusKey = entry.key)
        }
    }

    companion object {
        private const val STATE_SEARCHING = "searching"

        /** Home row key -> section screen. Order and keys must match res/xml/prefs_root.xml. */
        val SECTIONS = linkedMapOf(
            "section_detection" to Section(R.xml.prefs_detection, R.string.settings_section_detection),
            "section_rotation" to Section(R.xml.prefs_rotation, R.string.settings_section_rotation),
            "section_tile" to Section(R.xml.prefs_tile, R.string.settings_section_tile),
            "section_rules" to Section(R.xml.prefs_rules, R.string.settings_section_rules),
        )
    }
}
