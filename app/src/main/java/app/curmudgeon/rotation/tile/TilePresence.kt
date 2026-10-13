// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import app.curmudgeon.rotation.settings.Prefs

/**
 * Whether each of our tiles is in the Quick Settings panel. There is no API to ask, so the tile services record it
 * ([onAdded], [onRemoved]). There is no API to take a tile out either: the user drags it out
 * ([TileRequester.explainRemoving]). [id] is "rotate", "lock", "portrait" or [customId].
 */
object TilePresence {
    const val ROTATE = "rotate"
    const val LOCK = "lock"
    const val PORTRAIT = "portrait"

    fun customId(slot: Int) = "custom_$slot"

    fun isAdded(id: String): Boolean = Prefs.isTileAdded(id)

    /** From the tile service: added, or showing in the panel (so it must be there). */
    fun onAdded(id: String) = Prefs.setTileAdded(id, true)

    fun onRemoved(id: String) = Prefs.setTileAdded(id, false)
}
