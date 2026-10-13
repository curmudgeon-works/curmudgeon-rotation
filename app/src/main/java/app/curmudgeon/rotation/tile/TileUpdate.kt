// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.service.quicksettings.Tile
import android.util.Log
import app.curmudgeon.rotation.detect.EventLog

/**
 * [Tile.updateTile] that can't crash the app. Quick Settings throws back at us when it no longer knows the tile's
 * service (a custom tile slot switched off while the panel was showing it); a crash there also stops the
 * accessibility service until the user switches it off and on.
 */
fun Tile.updateSafely() {
    try {
        updateTile()
    } catch (e: RuntimeException) {
        Log.w(EventLog.TAG, "Quick Settings refused a tile update", e)
    }
}
