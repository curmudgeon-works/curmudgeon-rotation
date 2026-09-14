// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Common screen setup: toolbar as action bar (with a back arrow when [showUp]) plus [setUpEdgeToEdge].
 */
fun AppCompatActivity.setUpChrome(root: View, toolbar: Toolbar, showUp: Boolean) {
    setSupportActionBar(toolbar)
    if (showUp) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
    setUpEdgeToEdge(root)
}

/**
 * Edge-to-edge drawing on every Android version (enforced from Android 15 at targetSdk 35+),
 * padding [root] clear of the status bar, navigation bar, cutout and keyboard. Used directly by
 * screens with their own top bar instead of a Toolbar (settings).
 */
fun AppCompatActivity.setUpEdgeToEdge(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
