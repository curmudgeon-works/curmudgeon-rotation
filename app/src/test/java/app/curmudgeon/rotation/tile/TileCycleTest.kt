// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import app.curmudgeon.rotation.orientation.ManualModeResolver
import app.curmudgeon.rotation.orientation.OrientationMode.AUTO
import app.curmudgeon.rotation.orientation.OrientationMode.LANDSCAPE
import app.curmudgeon.rotation.orientation.OrientationMode.OFF
import app.curmudgeon.rotation.orientation.OrientationMode.PORTRAIT
import app.curmudgeon.rotation.orientation.OrientationMode.REVERSE_LANDSCAPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileCycleTest {
    @Test
    fun fourTapCycle() {
        assertEquals(AUTO, TileCycle.next(OFF, includeReverseLandscape = false))
        assertEquals(PORTRAIT, TileCycle.next(AUTO, includeReverseLandscape = false))
        assertEquals(LANDSCAPE, TileCycle.next(PORTRAIT, includeReverseLandscape = false))
        assertEquals(OFF, TileCycle.next(LANDSCAPE, includeReverseLandscape = false))
    }

    @Test
    fun cycleWithReverseLandscape() {
        assertEquals(REVERSE_LANDSCAPE, TileCycle.next(LANDSCAPE, includeReverseLandscape = true))
        assertEquals(OFF, TileCycle.next(REVERSE_LANDSCAPE, includeReverseLandscape = true))
    }

    @Test
    fun modeOutsideCycleReturnsToOff() {
        assertEquals(OFF, TileCycle.next(REVERSE_LANDSCAPE, includeReverseLandscape = false))
    }

    @Test
    fun rememberedModeVersusSystem() {
        assertTrue(ManualModeResolver.consistent(PORTRAIT, OFF))   // same system values, remembered choice wins
        assertTrue(ManualModeResolver.consistent(LANDSCAPE, LANDSCAPE))
        assertFalse(ManualModeResolver.consistent(LANDSCAPE, AUTO)) // auto-rotate turned on elsewhere
        assertFalse(ManualModeResolver.consistent(OFF, LANDSCAPE))
    }
}
