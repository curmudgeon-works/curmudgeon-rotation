// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import app.curmudgeon.rotation.Permissions
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivitySetupBinding
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.Prefs
import app.curmudgeon.rotation.tile.TileRequester

enum class SetupTopic(@param:StringRes val title: Int, @param:StringRes val body: Int, @param:StringRes val action: Int) {
    WRITE_SETTINGS(R.string.setup_write_settings_title, R.string.setup_write_settings_body, R.string.setup_open_settings),
    OVERLAY(R.string.setup_overlay_title, R.string.setup_overlay_body, R.string.setup_open_settings),
    ACCESSIBILITY(R.string.setup_accessibility_title, R.string.setup_accessibility_body, R.string.setup_accessibility_agree),
    USAGE_ACCESS(R.string.setup_usage_title, R.string.setup_usage_body, R.string.setup_open_settings),
    TILE(R.string.setup_tile_title, R.string.setup_tile_body, R.string.setup_tile_add),
}

/**
 * Explains a permission before sending the user to the system screen that grants it. For the
 * accessibility service this is the prominent disclosure Play policy requires: the user must
 * agree here before we open accessibility settings.
 */
class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding
    private lateinit var topic: SetupTopic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        topic = intent.getStringExtra(EXTRA_TOPIC)?.let { name -> SetupTopic.entries.firstOrNull { it.name == name } }
            ?: run { finish(); return }
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = true)

        binding.setupTitle.setText(topic.title)
        binding.setupBody.setText(topic.body)
        binding.primaryButton.setOnClickListener { onPrimary() }
        binding.secondaryButton.isVisible = topic == SetupTopic.ACCESSIBILITY
        binding.secondaryButton.setOnClickListener { Permissions.open(this, Permissions.appDetailsIntent(this)) }
        binding.dismissButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val done = isDone()
        if (done) {
            OrientationController.refresh()
            RotationService.sync(this)
        }
        binding.setupStatus.setText(if (done) R.string.setup_status_done else R.string.setup_status_missing)
        binding.primaryButton.isVisible = done || topic != SetupTopic.TILE || TileRequester.isSupported
        binding.primaryButton.setText(if (done) R.string.setup_done else topic.action)
        binding.dismissButton.isVisible = !done
    }

    private fun onPrimary() {
        if (isDone()) {
            finish()
            return
        }
        when (topic) {
            SetupTopic.WRITE_SETTINGS -> Permissions.open(this, Permissions.writeSettingsIntent(this))
            SetupTopic.OVERLAY -> Permissions.open(this, Permissions.overlayIntent(this))
            SetupTopic.ACCESSIBILITY -> Permissions.open(this, Permissions.accessibilityIntent())
            SetupTopic.USAGE_ACCESS -> Permissions.open(this, Permissions.usageAccessIntent())
            SetupTopic.TILE -> if (TileRequester.isSupported) TileRequester.request(this) { render() }
        }
    }

    private fun isDone(): Boolean = when (topic) {
        SetupTopic.WRITE_SETTINGS -> Permissions.canWriteSettings(this)
        SetupTopic.OVERLAY -> Permissions.canDrawOverlays(this)
        SetupTopic.ACCESSIBILITY -> Permissions.isAccessibilityServiceEnabled(this)
        SetupTopic.USAGE_ACCESS -> Permissions.hasUsageAccess(this)
        SetupTopic.TILE -> Prefs.tileAdded
    }

    companion object {
        private const val EXTRA_TOPIC = "topic"

        fun intent(context: Context, topic: SetupTopic): Intent =
            Intent(context, SetupActivity::class.java).putExtra(EXTRA_TOPIC, topic.name)
    }
}
