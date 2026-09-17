// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockStepTest {
    @Test
    fun fourTapsVisitEveryRotationOnceThenRelease() {
        val visited = generateSequence(LockStep.entries.first()) { it.next() }.toList()
        assertEquals(listOf(LockStep.LANDSCAPE, LockStep.UPSIDE_DOWN, LockStep.REVERSE_LANDSCAPE, LockStep.PORTRAIT), visited)
        assertEquals(
            setOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270),
            visited.map { it.userRotation }.toSet(),
        )
        assertNull(LockStep.PORTRAIT.next())
    }
}
