// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.settings.Prefs

/**
 * Tiles never receive long-presses; Android opens the tile's "preferences" activity instead. This invisible
 * activity is that target: it runs the long-press action from settings (default: toggle auto-rotate) and closes.
 */
class TileLongPressActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the long-press intent names the tile that was pressed; the Force landscape tile just opens the app
        @Suppress("DEPRECATION")
        val pressed = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        val action = if (pressed == LandscapeLockTileService.component(this)) TileAction.OPEN_APP else Prefs.tileLongPressAction
        val open = action.perform(this)
        if (open != null) {
            startActivity(open)
        } else {
            RotationService.sync(this)
            RotationTileService.requestUpdate(this)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
