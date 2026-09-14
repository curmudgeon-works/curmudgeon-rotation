// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivityRuleEditorBinding
import app.curmudgeon.rotation.databinding.ItemScreenBinding
import app.curmudgeon.rotation.detect.RecentActivityLog
import app.curmudgeon.rotation.orientation.OverlayHost
import app.curmudgeon.rotation.rules.AppRule
import app.curmudgeon.rotation.rules.RuleAction
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.rules.normalizeActivityName
import app.curmudgeon.rotation.settings.OverlayType
import app.curmudgeon.rotation.settings.DetectionMethod
import app.curmudgeon.rotation.settings.Prefs

/** Edits the rule for one app: the action, and optionally the screens (activities) it is limited to. */
class RuleEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRuleEditorBinding
    private lateinit var targetPackage: String
    private var existing: AppRule? = null
    private var action = RuleAction.AUTO_ROTATE_ON
    private val screens = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        binding = ActivityRuleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = true)

        existing = RuleStore.find(targetPackage)
        if (savedInstanceState == null) {
            existing?.let {
                action = it.action
                screens += it.activities
                binding.onlyScreensSwitch.isChecked = it.activities.isNotEmpty()
            }
        } else {
            action = savedInstanceState.getString(STATE_ACTION)?.let(RuleAction::fromId) ?: action
            screens += savedInstanceState.getStringArrayList(STATE_SCREENS).orEmpty()
        }

        val app = AppInfoLoader.load(this, targetPackage)
        binding.appIcon.setImageDrawable(app.icon)
        binding.appLabel.text = app.label
        binding.appPackage.text = targetPackage

        buildActionOptions()
        binding.onlyScreensSwitch.setOnCheckedChangeListener { _, _ -> renderScreens() }
        binding.addScreenButton.setOnClickListener { promptForScreen() }
        binding.saveButton.setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        renderRequirement()
        renderScreens()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTION, action.id)
        outState.putStringArrayList(STATE_SCREENS, ArrayList(screens))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_rule_editor, menu)
        menu.findItem(R.id.action_delete).isVisible = existing != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_delete) return super.onOptionsItemSelected(item)
        RuleStore.remove(targetPackage)
        finish()
        return true
    }

    private fun buildActionOptions() {
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.option_padding)
        RuleAction.entries.forEach { option ->
            val text = SpannableStringBuilder(getString(option.labelRes())).append('\n')
                .inSpans(ForegroundColorSpan(secondary), RelativeSizeSpan(0.85f)) { append(getString(option.descriptionRes())) }
            val button = RadioButton(this).apply {
                id = ViewGroup.generateViewId()
                this.text = text
                tag = option
                setPadding(paddingLeft, paddingTop + verticalPadding, paddingRight, paddingBottom + verticalPadding)
            }
            binding.actionGroup.addView(button, RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT))
            if (option == action) button.isChecked = true
        }
        binding.actionGroup.setOnCheckedChangeListener { group, checkedId ->
            action = group.findViewById<RadioButton>(checkedId).tag as RuleAction
            renderRequirement()
        }
    }

    /** Explains what the chosen action needs and whether it is in place, with a way to fix it. */
    private fun renderRequirement() {
        val (message, fixTopic) = when (action.mechanism) {
            RuleAction.Mechanism.NONE -> null to null
            RuleAction.Mechanism.SYSTEM_SETTING ->
                if (Permissions.canWriteSettings(this)) null to null else R.string.editor_needs_write_settings to SetupTopic.WRITE_SETTINGS
            RuleAction.Mechanism.OVERLAY -> if (OverlayHost.isAvailable(this)) {
                R.string.editor_overlay_note to null
            } else {
                val topic = if (Prefs.overlayType == OverlayType.ACCESSIBILITY) SetupTopic.ACCESSIBILITY else SetupTopic.OVERLAY
                R.string.editor_needs_overlay to topic
            }
        }
        binding.requirementCard.isVisible = message != null
        message?.let(binding.requirementText::setText)
        binding.requirementFix.isVisible = fixTopic != null
        binding.requirementFix.setOnClickListener { fixTopic?.let { startActivity(SetupActivity.intent(this, it)) } }
    }

    private fun renderScreens() {
        val limited = binding.onlyScreensSwitch.isChecked
        binding.screensSection.isVisible = limited
        if (!limited) return

        binding.selectedScreens.removeAllViews()
        binding.selectedEmpty.isVisible = screens.isEmpty()
        screens.forEach { name ->
            screenRow(binding.selectedScreens, name, R.string.editor_remove) {
                screens -= name
                renderScreens()
            }
        }

        val recent = RecentActivityLog.forPackage(targetPackage).map { it.activity }.filterNot { it in screens }
        binding.recentScreens.removeAllViews()
        binding.recentEmpty.isVisible = recent.isEmpty()
        recent.forEach { name ->
            screenRow(binding.recentScreens, name, R.string.editor_add) {
                screens += name
                renderScreens()
            }
        }
    }

    private fun screenRow(parent: ViewGroup, activity: String, buttonLabel: Int, onClick: () -> Unit) {
        val row = ItemScreenBinding.inflate(layoutInflater, parent, true)
        row.screenShortName.text = activity.substringAfterLast('.')
        row.screenFullName.text = activity
        row.screenButton.setText(buttonLabel)
        row.screenButton.setOnClickListener { onClick() }
    }

    private fun promptForScreen() {
        val input = EditText(this).apply {
            hint = getString(R.string.editor_add_screen_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
        }
        val container = FrameLayout(this).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.dialog_padding)
            setPadding(margin, 0, margin, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_add_screen)
            .setMessage(R.string.editor_add_screen_message)
            .setView(container)
            .setPositiveButton(R.string.editor_add) { _, _ ->
                val name = normalizeActivityName(targetPackage, input.text.toString())
                if (name.isNotEmpty()) {
                    screens += name
                    renderScreens()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        val limited = binding.onlyScreensSwitch.isChecked
        if (limited && screens.isEmpty()) {
            Toast.makeText(this, R.string.editor_no_screens, Toast.LENGTH_LONG).show()
            return
        }
        RuleStore.put(AppRule(targetPackage, action, if (limited) screens.toSet() else emptySet()))
        // a rule only runs if the app can tell which app is in front: ask for that now if it is missing
        if (action != RuleAction.FOLLOW_SYSTEM) {
            val detectionReady = when (Prefs.detectionMethod) {
                DetectionMethod.ACCESSIBILITY -> Permissions.isAccessibilityServiceEnabled(this)
                DetectionMethod.USAGE_STATS -> Permissions.hasUsageAccess(this)
            }
            if (!detectionReady) startActivity(SetupActivity.intent(this,
                if (Prefs.detectionMethod == DetectionMethod.ACCESSIBILITY) SetupTopic.ACCESSIBILITY else SetupTopic.USAGE_ACCESS))
        }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val STATE_ACTION = "action"
        private const val STATE_SCREENS = "screens"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, RuleEditorActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)
    }
}
