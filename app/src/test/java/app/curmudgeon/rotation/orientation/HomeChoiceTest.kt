// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.content.pm.ActivityInfo
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChoiceTest {
    private val current = SystemRotation(autoRotate = false, userRotation = Surface.ROTATION_270)

    @Test
    fun arrowTurnsClockwiseTopRightBottomLeft() {
        val seen = generateSequence(Surface.ROTATION_0) { HomeChoice.nextArrow(it) }.take(5).toList()
        assertEquals(listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270, Surface.ROTATION_0), seen)
    }

    @Test
    fun lockedFreezesWhereTheArrowPoints() {
        for (arrow in 0..3) {
            assertEquals(SystemRotation(autoRotate = false, userRotation = arrow), HomeChoice.LOCKED.system(current, arrow))
        }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, HomeChoice.LOCKED.overlay(Surface.ROTATION_0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, HomeChoice.LOCKED.overlay(Surface.ROTATION_90))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, HomeChoice.LOCKED.overlay(Surface.ROTATION_180))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, HomeChoice.LOCKED.overlay(Surface.ROTATION_270))
    }

    @Test
    fun onlyAutoLetsAppsDecide() {
        assertFalse(HomeChoice.AUTO.needsHardLock)
        assertNull(HomeChoice.AUTO.overlay(0))
        for (choice in HomeChoice.entries - HomeChoice.AUTO) assertTrue(choice.needsHardLock)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, HomeChoice.FOUR_WAY.overlay(0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, HomeChoice.PORTRAIT_FLIP.overlay(0))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, HomeChoice.PORTRAIT_FLIP.overlay(0, upsideDown = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, HomeChoice.LANDSCAPE_FLIP.overlay(0))
    }

    @Test
    fun systemFallbackMatchesTheChoice() {
        // what's left if the overlay goes: auto-rotate for the sensor choices, the matching lock for the flippable ones
        assertTrue(HomeChoice.AUTO.system(current, 0).autoRotate)
        assertTrue(HomeChoice.FOUR_WAY.system(current, 0).autoRotate)
        assertEquals(SystemRotation(false, Surface.ROTATION_0), HomeChoice.PORTRAIT_FLIP.system(current, 2))
        assertEquals(SystemRotation(false, Surface.ROTATION_90), HomeChoice.LANDSCAPE_FLIP.system(current, 2))
    }

    @Test
    fun storedValuesRoundTrip() {
        for (choice in HomeChoice.entries) assertEquals(choice, HomeChoice.of(choice.value))
        assertNull(HomeChoice.of(null))
        assertNull(HomeChoice.of("nonsense"))
    }
}
