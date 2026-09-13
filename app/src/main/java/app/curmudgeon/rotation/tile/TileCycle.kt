// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import app.curmudgeon.rotation.orientation.OrientationMode

object TileCycle {
    fun order(includeReverseLandscape: Boolean): List<OrientationMode> =
        if (includeReverseLandscape) {
            listOf(OrientationMode.AUTO, OrientationMode.PORTRAIT, OrientationMode.LANDSCAPE, OrientationMode.REVERSE_LANDSCAPE)
        } else {
            listOf(OrientationMode.AUTO, OrientationMode.PORTRAIT, OrientationMode.LANDSCAPE)
        }

    /** Mode after [current]. A mode outside the cycle (reverse landscape set elsewhere) goes back to the start, Auto. */
    fun next(current: OrientationMode, includeReverseLandscape: Boolean): OrientationMode {
        val order = order(includeReverseLandscape)
        return order[(order.indexOf(current) + 1) % order.size]
    }
}
