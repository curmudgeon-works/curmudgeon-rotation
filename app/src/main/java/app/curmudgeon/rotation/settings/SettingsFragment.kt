// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.curmudgeon.rotation.BuildConfig
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.detect.RecentActivityLog
import app.curmudgeon.rotation.orientation.OverlayHost
import app.curmudgeon.rotation.rules.RuleJson
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic
import org.json.JSONException
import java.io.IOException
import java.util.Date

/**
 * One settings screen (home or a section, see [SettingsActivity.SECTIONS]). Every preference is optional here, so
 * each listener is attached only on the screen that holds it. Hides advanced settings in simple mode, unless the
 * screen was opened from search.
 */
class SettingsFragment : PreferenceFragmentCompat() {
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(::exportRules)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importRules)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(requireArguments().getInt(ARG_XML), rootKey)
        SettingsActivity.SECTIONS.keys.forEach { key -> find<Preference>(key)?.widgetLayoutResource = R.layout.pref_chevron }

        numberPreference(PrefKeys.DEBOUNCE_MS, Prefs.DEBOUNCE_RANGE, R.string.pref_debounce_summary)
        numberPreference(PrefKeys.POLL_INTERVAL_MS, Prefs.POLL_INTERVAL_RANGE, R.string.pref_poll_interval_summary)
        find<EditTextPreference>(PrefKeys.IGNORED_PACKAGES)?.apply {
            setSummaryProvider {
                val count = Prefs.parsePackageList(text.orEmpty()).size
                if (count == 0) getString(R.string.pref_ignored_packages_none)
                else resources.getQuantityString(R.plurals.pref_ignored_packages_count, count, count)
            }
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                it.minLines = 3
            }
        }

        find<ListPreference>(PrefKeys.DETECTION_METHOD)?.setOnPreferenceChangeListener { _, value ->
            val context = requireContext()
            when (value) {
                DetectionMethod.ACCESSIBILITY.value ->
                    if (!Permissions.isAccessibilityServiceEnabled(context)) openSetup(SetupTopic.ACCESSIBILITY)
                DetectionMethod.USAGE_STATS.value ->
                    if (!Permissions.hasUsageAccess(context)) openSetup(SetupTopic.USAGE_ACCESS)
            }
            true
        }
        find<ListPreference>(PrefKeys.OVERLAY_TYPE)?.setOnPreferenceChangeListener { _, value ->
            offerOverlaySetup(value == OverlayType.ACCESSIBILITY.value)
            true
        }
        find<ListPreference>(PrefKeys.TILE_MECHANISM)?.setOnPreferenceChangeListener { _, value ->
            if (value == TileMechanism.OVERLAY.value && !OverlayHost.isAvailable(requireContext())) {
                offerOverlaySetup(Prefs.overlayType == OverlayType.ACCESSIBILITY)
            }
            true
        }
        find<Preference>(PrefKeys.QUICK_ACTIONS_NOTIFICATION)?.setOnPreferenceChangeListener { _, value ->
            if (value == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !Permissions.hasNotificationPermission(requireContext())
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            true
        }

        onClick(PrefKeys.EXPORT_RULES) { exportLauncher.launch(EXPORT_FILE_NAME) }
        onClick(PrefKeys.IMPORT_RULES) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        onClick(PrefKeys.RECENT_LOG) { showRecentLog() }
        find<Preference>(PrefKeys.ABOUT)?.summary = getString(R.string.pref_about_summary, BuildConfig.VERSION_NAME)
        onClick(PrefKeys.ABOUT) { showAbout() }

        applyMode()
    }

    @get:StringRes
    val titleRes: Int get() = requireArguments().getInt(ARG_TITLE)

    /**
     * Shows everything in advanced mode; in simple mode only [SimpleModeFilter.SIMPLE_KEYS] and categories that still
     * have visible children. Home rows hide in simple mode when their whole section is advanced. Opened from search:
     * everything shows.
     */
    fun applyMode() {
        val advanced = Prefs.advancedMode || requireArguments().getString(ARG_FOCUS) != null
        SimpleModeFilter.apply(preferenceScreen) { preference ->
            val section = SettingsActivity.SECTIONS[preference.key]
            if (section != null) advanced || SettingsIndex.hasSimple(requireContext(), section.xml)
            else SimpleModeFilter.isShown(preference.key, advanced)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) requireArguments().getString(ARG_FOCUS)?.let { key -> view.post { scrollToPreference(key) } }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val section = SettingsActivity.SECTIONS[preference.key]
        val activity = activity as? SettingsActivity
        if (section == null || activity == null) return super.onPreferenceTreeClick(preference)
        activity.show(section.xml, section.title)
        return true
    }

    /** Settings live on different screens, so any of them may be absent from this one. */
    private fun <T : Preference> find(key: String): T? = findPreference(key)

    private fun onClick(key: String, action: () -> Unit) {
        find<Preference>(key)?.setOnPreferenceClickListener {
            action()
            true
        }
    }

    /** Number entry clamped to [range]; out-of-range input is rejected with a hint. */
    private fun numberPreference(key: String, range: LongRange, summaryRes: Int) {
        find<EditTextPreference>(key)?.apply {
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
            setSummaryProvider { getString(summaryRes, text) }
            setOnPreferenceChangeListener { _, value ->
                val valid = (value as? String)?.trim()?.toLongOrNull()?.let { it in range } == true
                if (!valid) toast(getString(R.string.pref_number_out_of_range, range.first, range.last))
                valid
            }
        }
    }

    private fun offerOverlaySetup(accessibilityOverlay: Boolean) {
        val context = requireContext()
        if (accessibilityOverlay && !Permissions.isAccessibilityServiceEnabled(context)) openSetup(SetupTopic.ACCESSIBILITY)
        if (!accessibilityOverlay && !Permissions.canDrawOverlays(context)) openSetup(SetupTopic.OVERLAY)
    }

    private fun openSetup(topic: SetupTopic) = startActivity(SetupActivity.intent(requireContext(), topic))

    private fun exportRules(uri: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(RuleJson.toJson(RuleStore.all()).toByteArray())
            } ?: throw IOException("No output stream")
            toast(resources.getQuantityString(R.plurals.rules_exported, RuleStore.all().size, RuleStore.all().size))
        } catch (e: IOException) {
            toast(getString(R.string.rules_export_failed, e.localizedMessage))
        } catch (e: SecurityException) {
            toast(getString(R.string.rules_export_failed, e.localizedMessage))
        }
    }

    private fun importRules(uri: Uri) {
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IOException("No input stream")
            val rules = RuleJson.fromJson(text)
            RuleStore.merge(rules)
            toast(resources.getQuantityString(R.plurals.rules_imported, rules.size, rules.size))
        } catch (e: IOException) {
            toast(getString(R.string.rules_import_failed, e.localizedMessage))
        } catch (e: JSONException) {
            toast(getString(R.string.rules_import_failed, e.localizedMessage))
        } catch (e: SecurityException) {
            toast(getString(R.string.rules_import_failed, e.localizedMessage))
        }
    }

    private fun showRecentLog() {
        val context = requireContext()
        val timeFormat = DateFormat.getTimeFormat(context)
        val dateFormat = DateFormat.getDateFormat(context)
        val entries = RecentActivityLog.all()
        val builder = AlertDialog.Builder(context).setTitle(R.string.pref_recent_log)
        if (entries.isEmpty()) {
            builder.setMessage(R.string.recent_log_empty)
        } else {
            val items = entries.map {
                val date = Date(it.lastSeen)
                "${dateFormat.format(date)} ${timeFormat.format(date)}\n${it.packageName}\n${it.activity}"
            }.toTypedArray<CharSequence>()
            builder.setItems(items, null)
            builder.setNeutralButton(R.string.recent_log_clear) { _, _ -> RecentActivityLog.clear() }
        }
        builder.setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showAbout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    companion object {
        private const val EXPORT_FILE_NAME = "curmudgeon-rotation-rules.json"
        private const val ARG_XML = "xml"
        private const val ARG_TITLE = "title"
        private const val ARG_FOCUS = "focus"

        /** Screen [xml] titled [title]; [focusKey] (from search) scrolls to that setting and shows the whole screen. */
        fun newInstance(@XmlRes xml: Int, @StringRes title: Int, focusKey: String? = null) = SettingsFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_XML, xml)
                putInt(ARG_TITLE, title)
                putString(ARG_FOCUS, focusKey)
            }
        }
    }
}
