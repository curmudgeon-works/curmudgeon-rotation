// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import app.curmudgeon.rotation.orientation.ManualModeResolver
import app.curmudgeon.rotation.orientation.OrientationMode.AUTO
import app.curmudgeon.rotation.orientation.OrientationMode.LANDSCAPE
import app.curmudgeon.rotation.orientation.OrientationMode.OFF
import app.curmudgeon.rotation.orientation.OrientationMode.PORTRAIT
import app.curmudgeon.rotation.orientation.OrientationMode.REVERSE_LANDSCAPE
import app.curmudgeon.rotation.tile.TileAction.CYCLE
import app.curmudgeon.rotation.tile.TileAction.CYCLE_WITH_REVERSE
import app.curmudgeon.rotation.tile.TileAction.OPEN_APP
import app.curmudgeon.rotation.tile.TileAction.TOGGLE_AUTO_ROTATE
import app.curmudgeon.rotation.tile.TileAction.TOGGLE_ORIENTATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileActionTest {
    @Test
    fun orientationToggleFlipsLocks() {
        assertEquals(LANDSCAPE, TOGGLE_ORIENTATION.target(PORTRAIT, displayLandscape = true))
        assertEquals(PORTRAIT, TOGGLE_ORIENTATION.target(LANDSCAPE, displayLandscape = false))
        assertEquals(PORTRAIT, TOGGLE_ORIENTATION.target(REVERSE_LANDSCAPE, displayLandscape = true))
    }

    @Test
    fun orientationToggleWithoutLockFlipsTheScreen() {
        assertEquals(LANDSCAPE, TOGGLE_ORIENTATION.target(OFF, displayLandscape = false))
        assertEquals(PORTRAIT, TOGGLE_ORIENTATION.target(OFF, displayLandscape = true))  // an app is showing landscape
        assertEquals(LANDSCAPE, TOGGLE_ORIENTATION.target(AUTO, displayLandscape = false))
        assertEquals(PORTRAIT, TOGGLE_ORIENTATION.target(AUTO, displayLandscape = true))
    }

    @Test
    fun fourStepCycle() {
        assertEquals(AUTO, CYCLE.target(OFF, false))
        assertEquals(PORTRAIT, CYCLE.target(AUTO, false))
        assertEquals(LANDSCAPE, CYCLE.target(PORTRAIT, false))
        assertEquals(OFF, CYCLE.target(LANDSCAPE, true))
        assertEquals(OFF, CYCLE.target(REVERSE_LANDSCAPE, true)) // outside the cycle
    }

    @Test
    fun cycleWithReverseLandscape() {
        assertEquals(REVERSE_LANDSCAPE, CYCLE_WITH_REVERSE.target(LANDSCAPE, true))
        assertEquals(OFF, CYCLE_WITH_REVERSE.target(REVERSE_LANDSCAPE, true))
    }

    @Test
    fun autoRotateToggleAndOpenApp() {
        assertEquals(OFF, TOGGLE_AUTO_ROTATE.target(AUTO, false))
        assertEquals(AUTO, TOGGLE_AUTO_ROTATE.target(LANDSCAPE, true))
        assertNull(OPEN_APP.target(AUTO, false))
        assertNull(TileAction.FLIP.target(AUTO, false)) // not a mode: OrientationController.flip()
    }

    @Test
    fun rememberedModeVersusSystem() {
        assertTrue(ManualModeResolver.consistent(PORTRAIT, OFF))   // same system values, remembered choice wins
        assertTrue(ManualModeResolver.consistent(LANDSCAPE, LANDSCAPE))
        assertFalse(ManualModeResolver.consistent(LANDSCAPE, AUTO)) // auto-rotate turned on elsewhere
        assertFalse(ManualModeResolver.consistent(OFF, LANDSCAPE))
    }
}
