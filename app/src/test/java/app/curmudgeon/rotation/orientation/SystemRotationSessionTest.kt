// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRotationSessionTest {
    private val autoOn = SystemRotation(autoRotate = true, userRotation = 0)
    private val portraitLock = SystemRotation(autoRotate = false, userRotation = 0)
    private val landscapeLock = SystemRotation(autoRotate = false, userRotation = 1)

    private class FakeSettings(var value: SystemRotation) : SystemRotationAccess {
        override fun read() = value
        override fun write(value: SystemRotation) {
            this.value = value
        }
    }

    private class MemoryStore : SystemRotationStateStore {
        override var snapshot: SystemRotation? = null
        override var lastWritten: SystemRotation? = null
    }

    private val settings = FakeSettings(autoOn)
    private val session = SystemRotationSession(settings, MemoryStore())

    @Test
    fun restoresOriginalValuesWhenRuleEnds() {
        session.applyRuleTarget(landscapeLock)
        assertEquals(landscapeLock, settings.value)
        assertTrue(session.end(restore = true))
        assertEquals(autoOn, settings.value)
        assertFalse(session.isActive)
    }

    @Test
    fun switchingBetweenRuleAppsKeepsFirstSnapshot() {
        session.applyRuleTarget(landscapeLock)
        session.applyRuleTarget(portraitLock)
        session.end(restore = true)
        assertEquals(autoOn, settings.value)
    }

    @Test
    fun restoreDisabledLeavesRuleValues() {
        session.applyRuleTarget(landscapeLock)
        assertFalse(session.end(restore = false))
        assertEquals(landscapeLock, settings.value)
        assertFalse(session.isActive)
    }

    @Test
    fun userChangeDuringRuleIsNotOverwritten() {
        session.applyRuleTarget(landscapeLock)
        settings.value = portraitLock // user flips the system rotation tile
        assertFalse(session.end(restore = true))
        assertEquals(portraitLock, settings.value)
    }

    @Test
    fun userChangeBetweenRulesBecomesNewSnapshot() {
        session.applyRuleTarget(landscapeLock)
        settings.value = portraitLock
        session.applyRuleTarget(autoOn)
        session.end(restore = true)
        assertEquals(portraitLock, settings.value)
    }

    @Test
    fun manualChangeEndsSessionWithoutRestore() {
        session.applyRuleTarget(landscapeLock)
        session.applyManual(portraitLock)
        assertFalse(session.isActive)
        assertFalse(session.end(restore = true))
        assertEquals(portraitLock, settings.value)
    }

    @Test
    fun userRotationIsIgnoredWhileAutoRotating() {
        session.applyRuleTarget(SystemRotation(autoRotate = true, userRotation = 1))
        settings.value = SystemRotation(autoRotate = true, userRotation = 3)
        assertTrue(session.end(restore = true))
    }

    @Test
    fun endWithoutRuleDoesNothing() {
        assertFalse(session.end(restore = true))
        assertEquals(autoOn, settings.value)
    }
}
