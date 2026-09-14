// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureTest {
    @Test
    fun anglesToPosture() {
        assertEquals(Posture.PORTRAIT, Posture.of(0))
        assertEquals(Posture.PORTRAIT, Posture.of(350))
        assertEquals(Posture.PORTRAIT, Posture.of(185))  // upside down
        assertEquals(Posture.LANDSCAPE, Posture.of(90))
        assertEquals(Posture.LANDSCAPE, Posture.of(265))
        assertEquals(Posture.UNKNOWN, Posture.of(45))    // halfway
        assertEquals(Posture.UNKNOWN, Posture.of(-1))    // flat
    }

    @Test
    fun turnCountsOnlyOnceHeld() {
        val tracker = TurnTracker(Posture.LANDSCAPE, stableMs = 300)
        assertFalse(tracker.onSample(Posture.PORTRAIT, 0))
        assertFalse(tracker.onSample(Posture.LANDSCAPE, 100))
        assertFalse(tracker.onSample(Posture.LANDSCAPE, 300))
        assertTrue(tracker.onSample(Posture.LANDSCAPE, 400))
    }

    @Test
    fun flipStartKnowsWhichWayItsLockPoints() {
        assertEquals(false, FlipStart(SystemRotation(autoRotate = false, userRotation = 0), OrientationMode.PORTRAIT).lockedLandscape())
        assertEquals(true, FlipStart(SystemRotation(autoRotate = false, userRotation = 1), OrientationMode.LANDSCAPE).lockedLandscape())
        assertEquals(true, FlipStart(SystemRotation(autoRotate = false, userRotation = 3), OrientationMode.REVERSE_LANDSCAPE).lockedLandscape())
        assertEquals(null, FlipStart(SystemRotation(autoRotate = true, userRotation = 1), OrientationMode.AUTO).lockedLandscape())
    }

    @Test
    fun wobbleRestartsTheWait() {
        val tracker = TurnTracker(Posture.LANDSCAPE, stableMs = 300)
        assertFalse(tracker.onSample(Posture.LANDSCAPE, 0))
        assertFalse(tracker.onSample(Posture.UNKNOWN, 200))
        assertFalse(tracker.onSample(Posture.LANDSCAPE, 350))
        assertTrue(tracker.onSample(Posture.LANDSCAPE, 650))
    }
}
