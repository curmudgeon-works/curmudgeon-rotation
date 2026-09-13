// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebouncerTest {
    private val youtube = WindowEvent("com.google.android.youtube", "Watch")
    private val hbo = WindowEvent("com.wbd.stream", "Player")
    private val debouncer = Debouncer<WindowEvent>()

    @Test
    fun deliversOnlyAfterDelay() {
        assertEquals(1300L, debouncer.submit(youtube, now = 1000, delayMs = 300))
        assertNull(debouncer.poll(1299))
        assertEquals(youtube, debouncer.poll(1300))
        assertNull(debouncer.poll(1400))
    }

    @Test
    fun burstDeliversOnlyLatest() {
        debouncer.submit(youtube, now = 0, delayMs = 300)
        assertEquals(400L, debouncer.submit(hbo, now = 100, delayMs = 300))
        assertNull(debouncer.poll(300))
        assertEquals(hbo, debouncer.poll(400))
    }

    @Test
    fun repeatOfDeliveredValueIsDropped() {
        debouncer.submit(youtube, now = 0, delayMs = 0)
        assertEquals(youtube, debouncer.poll(0))
        assertNull(debouncer.submit(youtube, now = 10, delayMs = 300))
        assertNull(debouncer.poll(1000))
    }

    @Test
    fun returningToCurrentAppCancelsPendingChange() {
        debouncer.submit(youtube, now = 0, delayMs = 0)
        debouncer.poll(0)
        debouncer.submit(hbo, now = 10, delayMs = 300) // brief flash of another app
        assertNull(debouncer.submit(youtube, now = 50, delayMs = 300))
        assertNull(debouncer.poll(1000))
    }

    @Test
    fun resetForgetsDeliveredValue() {
        debouncer.submit(youtube, now = 0, delayMs = 0)
        debouncer.poll(0)
        debouncer.reset()
        assertEquals(300L, debouncer.submit(youtube, now = 0, delayMs = 300))
    }
}
