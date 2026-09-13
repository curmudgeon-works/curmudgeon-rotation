// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivitySettingsBinding
import app.curmudgeon.rotation.ui.setUpChrome

/** Hosts [SettingsFragment]; the toolbar's "Advanced" switch toggles which settings are shown. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settingsContainer, SettingsFragment()).commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        val switch = menu.findItem(R.id.action_advanced).actionView?.findViewById<SwitchCompat>(R.id.advancedSwitch)
        switch?.isChecked = Prefs.advancedMode
        switch?.setOnCheckedChangeListener { _, checked ->
            Prefs.advancedMode = checked
            (supportFragmentManager.findFragmentById(R.id.settingsContainer) as? SettingsFragment)?.applyMode(checked)
        }
        return true
    }
}
