// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.view.Surface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFlipTest {
    private val home = SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_0)
    private val locked = SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_90)

    private fun flip(startedOver: String?) = ActiveFlip(FlipStart(home, OrientationMode.PORTRAIT), locked, toLandscape = true, startedOver = startedOver)

    @Test
    fun anotherAppLeavesAWaitingFlipBehind() {
        assertTrue(flip("com.google.android.youtube").leftBehindBy("app.curmudgeon.browser"))
    }

    @Test
    fun theSameAppKeepsIt() {
        assertFalse(flip("com.google.android.youtube").leftBehindBy("com.google.android.youtube"))
    }

    @Test
    fun onceTurnedTheSensorEndsItNotTheApp() {
        val turned = flip("com.google.android.youtube").apply { phase = FlipPhase.TURNED }
        assertFalse(turned.leftBehindBy("app.curmudgeon.browser"))
    }

    @Test
    fun anUnknownStartAdoptsTheFirstAppItHearsOf() {
        val flip = flip(null)
        assertFalse(flip.leftBehindBy("com.google.android.youtube"))
        assertFalse(flip.leftBehindBy("com.google.android.youtube"))
        assertTrue(flip.leftBehindBy("app.curmudgeon.browser"))
    }
}
