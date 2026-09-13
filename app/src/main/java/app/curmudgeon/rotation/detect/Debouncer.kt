// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

/**
 * Collapses bursts of foreground changes (app launch animations, trampolines, a dialog flashing
 * past) into one: only the latest value is delivered, once no newer value arrived for the delay.
 * A value equal to the last delivered one cancels anything pending, since nothing changed.
 * Pure logic; the caller supplies time and schedules [poll].
 */
class Debouncer<T : Any> {
    private var pending: T? = null
    private var dueAt = 0L
    private var lastDelivered: T? = null

    /** Records [value] seen at [now]. Returns when [poll] should run, or null if nothing is pending. */
    fun submit(value: T, now: Long, delayMs: Long): Long? {
        if (value == lastDelivered) {
            pending = null
            return null
        }
        pending = value
        dueAt = now + delayMs
        return dueAt
    }

    /** Returns the pending value if it is due at [now], marking it delivered. */
    fun poll(now: Long): T? {
        val value = pending ?: return null
        if (now < dueAt) return null
        pending = null
        lastDelivered = value
        return value
    }

    fun reset() {
        pending = null
        lastDelivered = null
    }
}
