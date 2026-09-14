// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.app.Activity
import android.os.Bundle
import app.curmudgeon.rotation.orientation.OrientationController
import app.curmudgeon.rotation.service.RotationService
import app.curmudgeon.rotation.ui.SetupActivity
import app.curmudgeon.rotation.ui.SetupTopic

/**
 * Tiles never receive long-presses; Android opens the tile's "preferences" activity instead. This invisible
 * activity is that target: it toggles auto-rotate and closes immediately.
 */
class TileLongPressActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OrientationController.toggleAutoRotate()) {
            RotationService.sync(this)
            RotationTileService.requestUpdate(this)
        } else {
            startActivity(SetupActivity.intent(this, SetupTopic.WRITE_SETTINGS))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
