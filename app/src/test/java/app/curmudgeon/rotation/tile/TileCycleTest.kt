// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import app.curmudgeon.rotation.orientation.OrientationMode.AUTO
import app.curmudgeon.rotation.orientation.OrientationMode.LANDSCAPE
import app.curmudgeon.rotation.orientation.OrientationMode.PORTRAIT
import app.curmudgeon.rotation.orientation.OrientationMode.REVERSE_LANDSCAPE
import org.junit.Assert.assertEquals
import org.junit.Test

class TileCycleTest {
    @Test
    fun threeStateCycle() {
        assertEquals(PORTRAIT, TileCycle.next(AUTO, includeReverseLandscape = false))
        assertEquals(LANDSCAPE, TileCycle.next(PORTRAIT, includeReverseLandscape = false))
        assertEquals(AUTO, TileCycle.next(LANDSCAPE, includeReverseLandscape = false))
    }

    @Test
    fun fourStateCycle() {
        assertEquals(REVERSE_LANDSCAPE, TileCycle.next(LANDSCAPE, includeReverseLandscape = true))
        assertEquals(AUTO, TileCycle.next(REVERSE_LANDSCAPE, includeReverseLandscape = true))
    }

    @Test
    fun modeOutsideCycleReturnsToAuto() {
        assertEquals(AUTO, TileCycle.next(REVERSE_LANDSCAPE, includeReverseLandscape = false))
    }
}
