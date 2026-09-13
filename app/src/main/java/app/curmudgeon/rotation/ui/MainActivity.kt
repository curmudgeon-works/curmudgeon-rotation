// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivityMainBinding
import app.curmudgeon.rotation.databinding.ItemStatusRowBinding
import app.curmudgeon.rotation.detect.AccessibilityDetectionService
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.rules.RuleStore
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.DetectionMethod
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.settings.SettingsActivity
import app.curmudgeon.rotation.settings.TileMechanism
import app.curmudgeon.rotation.tile.TileRequester

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
        binding.addRuleButton.setOnClickListener { startActivity(Intent(this, AppPickerActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        // permissions may have changed while we were away
        OrientationController.refresh()
        RotationService.sync(this)
        RuleStore.addListener(onChange)
        OrientationController.addListener(onChange)
        render()
    }

    override fun onPause() {
        RuleStore.removeListener(onChange)
        OrientationController.removeListener(onChange)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_settings) return super.onOptionsItemSelected(item)
        startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    private fun render() {
        renderStatus()
        val mode = getString(OrientationController.currentMode().labelRes())
        val mechanism = getString(
            if (Prefs.tileMechanism == TileMechanism.SYSTEM_SETTING) R.string.mechanism_system else R.string.mechanism_overlay,
        )
        binding.currentMode.text = getString(R.string.main_current_mode, mode, mechanism)
        val rules = RuleStore.all()
        rulesAdapter.submitList(rules)
        binding.rulesEmpty.isVisible = rules.isEmpty()
    }

    private fun renderStatus() {
        binding.statusRows.removeAllViews()

        statusRow(
            R.string.status_tile, ok = Prefs.tileAdded,
            detail = if (Prefs.tileAdded) R.string.status_tile_ok else R.string.status_tile_missing,
            fixLabel = R.string.status_add,
        ) {
            if (TileRequester.isSupported) TileRequester.request(this) { render() } else openSetup(SetupTopic.TILE)
        }

        val canWrite = Permissions.canWriteSettings(this)
        statusRow(
            R.string.status_write_settings, ok = canWrite,
            detail = if (canWrite) R.string.status_write_settings_ok else R.string.status_write_settings_missing,
        ) { openSetup(SetupTopic.WRITE_SETTINGS) }

        val canOverlay = Permissions.canDrawOverlays(this)
        statusRow(
            R.string.status_overlay, ok = canOverlay,
            detail = if (canOverlay) R.string.status_overlay_ok else R.string.status_overlay_missing,
        ) { openSetup(SetupTopic.OVERLAY) }

        when (Prefs.detectionMethod) {
            DetectionMethod.ACCESSIBILITY -> {
                val connected = AccessibilityDetectionService.isConnected
                val detail = when {
                    connected -> R.string.status_accessibility_ok
                    Permissions.isAccessibilityServiceEnabled(this) -> R.string.status_accessibility_starting
                    else -> R.string.status_accessibility_missing
                }
                statusRow(R.string.status_detection_accessibility, ok = connected, detail = detail) {
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
                statusRow(R.string.status_detection_usage, ok = running, detail = detail) {
                    if (access) RotationService.sync(this) else openSetup(SetupTopic.USAGE_ACCESS)
                    render()
                }
            }
        }
    }

    private fun statusRow(
        @StringRes title: Int,
        ok: Boolean,
        @StringRes detail: Int,
        @StringRes fixLabel: Int = R.string.status_fix,
        fix: () -> Unit,
    ) {
        val row = ItemStatusRowBinding.inflate(layoutInflater, binding.statusRows, true)
        row.statusTitle.setText(title)
        row.statusDetail.setText(detail)
        row.statusIndicator.setImageResource(if (ok) R.drawable.ic_status_ok else R.drawable.ic_status_missing)
        row.statusFix.isVisible = !ok
        row.statusFix.setText(fixLabel)
        row.statusFix.setOnClickListener { fix() }
    }

    private fun openSetup(topic: SetupTopic) = startActivity(SetupActivity.intent(this, topic))
}
