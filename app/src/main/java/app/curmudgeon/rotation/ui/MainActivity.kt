// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivityMainBinding
import app.curmudgeon.rotation.databinding.ItemRotateOptionBinding
import app.curmudgeon.rotation.databinding.ItemStatusRowBinding
import app.curmudgeon.rotation.detect.AccessibilityDetectionService
import app.curmudgeon.rotation.detect.ForegroundTracker
import app.curmudgeon.rotation.orientation.HomeChoice
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.DetectionMethod
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.settings.SettingsActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val rulesAdapter = RulesAdapter { rule -> startActivity(RuleEditorActivity.intent(this, rule.packageName)) }
    private val onChange: () -> Unit = { runOnUiThread(::render) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = false)

        binding.rulesList.layoutManager = LinearLayoutManager(this)
        binding.rulesList.adapter = rulesAdapter
        binding.addRuleRow.setOnClickListener { startActivity(Intent(this, AppPickerActivity::class.java)) }
        binding.rulesWin.setOnClickListener {
            Prefs.rulesWin = binding.rulesWin.isChecked
            ForegroundTracker.onRulesWinChanged()
            render()
        }
        binding.rulesRow.setOnClickListener {
            Prefs.rulesUnfolded = !Prefs.rulesUnfolded
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        // permissions may have changed while we were away
        OrientationController.refresh()
        RotationService.sync(this)
        RuleStore.addListener(onChange)
        OrientationController.addListener(onChange)
        render()
        // the one permission everything needs: ask on first launch, then leave it to the status card
        if (!Permissions.canWriteSettings(this) && !Prefs.askedWriteSettings) {
            Prefs.askedWriteSettings = true
            openSetup(SetupTopic.WRITE_SETTINGS)
        }
    }

    override fun onPause() {
        RuleStore.removeListener(onChange)
        OrientationController.removeListener(onChange)
        super.onPause()
    }

    private fun render() {
        renderRotate()
        renderStatus()
        rulesAdapter.submitList(RuleStore.all())
        binding.rulesBody.isVisible = Prefs.rulesUnfolded
        binding.rulesFold.rotation = if (Prefs.rulesUnfolded) 270f else 90f
    }

    /** The "Rotate" section: what holds rotation now, System default and the five choices, and whether per-app rules win. */
    private fun renderRotate() {
        val selected = OrientationController.selectedHome()
        binding.currentMode.text = getString(R.string.rotate_now, currentText(selected))
        binding.rotateOptions.removeAllViews()
        ItemRotateOptionBinding.inflate(layoutInflater, binding.rotateOptions, true).apply {
            optionRadio.isChecked = OrientationController.isSystemDefault()
            optionTitle.setText(R.string.rotate_system)
            optionSummary.setText(R.string.rotate_system_summary)
            root.setOnClickListener { OrientationController.setSystemDefault() }
        }
        for (choice in HomeChoice.entries) {
            val row = ItemRotateOptionBinding.inflate(layoutInflater, binding.rotateOptions, true)
            val usable = !choice.needsHardLock || OrientationController.canForce()
            row.optionRadio.isChecked = choice == selected
            row.optionTitle.setText(choice.titleRes())
            row.optionSummary.text = when {
                !usable -> getString(R.string.rotate_needs_hard_lock)
                choice == HomeChoice.LOCKED -> getString(R.string.rotate_locked_summary, direction(Prefs.lockArrow))
                else -> getString(choice.summaryRes())
            }
            row.root.alpha = if (usable) 1f else 0.5f
            row.root.setOnClickListener { pick(choice) }
            if (choice == HomeChoice.LOCKED) {
                row.optionArrow.isVisible = true
                // drawn as the phone is held right now; the arrow points where the top of the picture will sit on it
                val held = display?.rotation ?: Surface.ROTATION_0
                row.optionArrowPhone.rotation = -90f * held
                row.optionArrowPointer.rotation = 90f * (Prefs.lockArrow - held)
                row.optionArrow.contentDescription = getString(R.string.rotate_turn_arrow, direction(Prefs.lockArrow))
                row.optionArrow.setOnClickListener { OrientationController.turnArrow() }
            }
        }
        binding.rulesWin.isChecked = Prefs.rulesWin
    }

    private fun pick(choice: HomeChoice) {
        if (OrientationController.setHome(choice)) return
        openSetup(if (!Permissions.canWriteSettings(this)) SetupTopic.WRITE_SETTINGS else SetupTopic.ACCESSIBILITY)
    }

    private fun currentText(selected: HomeChoice?): String {
        val mode = OrientationController.currentMode()
        flipLabel(mode)?.let { return getString(R.string.rotate_now_tile, getString(R.string.tile_label), it) }
        if (OrientationController.isLandscapeForced) {
            return getString(R.string.rotate_now_tile, getString(R.string.tile_force_landscape_label), getString(R.string.rotate_now_lock))
        }
        if (OrientationController.isPortraitForced) {
            return getString(R.string.rotate_now_tile, getString(R.string.tile_force_portrait_label), getString(R.string.rotate_now_portrait))
        }
        return when (selected) {
            null -> OrientationController.systemRotation().let {
                if (it.autoRotate) getString(R.string.rotate_now_system_auto)
                else getString(R.string.rotate_now_system, direction(it.userRotation))
            }
            HomeChoice.LOCKED -> getString(R.string.rotate_now_locked, direction(Prefs.lockArrow))
            else -> getString(selected.titleRes())
        }
    }

    private fun direction(rotation: Int): String = getString(
        when (rotation) {
            Surface.ROTATION_90 -> R.string.rotate_direction_right
            Surface.ROTATION_180 -> R.string.rotate_direction_bottom
            Surface.ROTATION_270 -> R.string.rotate_direction_left
            else -> R.string.rotate_direction_top
        },
    )

    private fun renderStatus() {
        binding.statusRows.removeAllViews()

        val canWrite = Permissions.canWriteSettings(this)
        statusRow(
            getString(R.string.status_write_settings),
            getString(if (canWrite) R.string.status_write_settings_ok else R.string.status_write_settings_missing),
            fix = if (canWrite) null else ({ openSetup(SetupTopic.WRITE_SETTINGS) }),
        )

        when (Prefs.detectionMethod) {
            DetectionMethod.ACCESSIBILITY -> {
                val connected = AccessibilityDetectionService.isConnected
                val detail = when {
                    connected -> R.string.status_accessibility_ok
                    Permissions.isAccessibilityServiceEnabled(this) -> R.string.status_accessibility_starting
                    else -> R.string.status_accessibility_missing
                }
                statusRow(getString(R.string.status_detection_accessibility), getString(detail), checked = connected) {
                    openSetup(SetupTopic.ACCESSIBILITY)
                }
            }
            DetectionMethod.USAGE_STATS -> {
                val access = Permissions.hasUsageAccess(this)
                val running = access && RotationService.isRunning
                val detail = when {
                    running -> R.string.status_usage_ok
                    access -> R.string.status_usage_not_running
                    else -> R.string.status_usage_missing
                }
                statusRow(getString(R.string.status_detection_usage), getString(detail), checked = running) {
                    if (access) RotationService.sync(this) else openSetup(SetupTopic.USAGE_ACCESS)
                    render()
                }
            }
        }

        statusRow(getString(R.string.settings_title), getString(R.string.status_settings_summary)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        statusRow(getString(R.string.status_tiles), getString(R.string.settings_section_tile_summary)) {
            startActivity(SettingsActivity.intent(this, "section_tile"))
        }
    }

    /** One row: a [fix] button when given, a state-only checkbox on the right when [checked] is given, tappable when [open] is given. */
    private fun statusRow(
        title: String,
        detail: String,
        checked: Boolean? = null,
        fix: (() -> Unit)? = null,
        open: (() -> Unit)? = null,
    ) {
        val row = ItemStatusRowBinding.inflate(layoutInflater, binding.statusRows, true)
        row.statusTitle.text = title
        row.statusDetail.text = detail
        row.statusFix.isVisible = fix != null
        row.statusFix.setOnClickListener { fix?.invoke() }
        row.statusCheck.isVisible = checked != null
        row.statusCheck.isChecked = checked == true
        if (open != null) {
            row.root.setBackgroundResource(TypedValue().also { theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId)
            row.root.setOnClickListener { open() }
        }
    }

    private fun openSetup(topic: SetupTopic) = startActivity(SetupActivity.intent(this, topic))
}
