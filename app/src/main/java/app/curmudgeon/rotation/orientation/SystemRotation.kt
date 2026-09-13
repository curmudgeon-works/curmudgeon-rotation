// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

/** The two Settings.System values that decide rotation for apps that follow the system. */
data class SystemRotation(val autoRotate: Boolean, val userRotation: Int) {
    /** True when [current] is still what we wrote. User rotation is irrelevant while auto-rotate is on. */
    fun isStillIn(current: SystemRotation): Boolean =
        autoRotate == current.autoRotate && (autoRotate || userRotation == current.userRotation)
}

interface SystemRotationAccess {
    fun read(): SystemRotation
    fun write(value: SystemRotation)
}

/** Persists the rule session across process death. */
interface SystemRotationStateStore {
    /** The user's own values from before the first rule changed them, or null when no rule is active. */
    var snapshot: SystemRotation?
    /** What a rule last wrote, used to notice manual changes made while the rule was active. */
    var lastWritten: SystemRotation?
}

/**
 * Remembers and restores the user's rotation settings around rule-driven changes.
 *
 * - The first rule to change the settings snapshots the user's values; switching between apps
 *   with rules keeps that original snapshot.
 * - If the user changes rotation themselves while a rule is active (system tile, rotation
 *   suggestion button), their new values become the snapshot, and they are never overwritten
 *   by a restore of older values.
 * - Changes from this app's own tile or notification are treated as the user's choice: they
 *   end the session without restoring anything.
 */
class SystemRotationSession(
    private val access: SystemRotationAccess,
    private val store: SystemRotationStateStore,
) {
    val isActive: Boolean get() = store.snapshot != null

    fun applyRuleTarget(target: SystemRotation) {
        val current = access.read()
        val written = store.lastWritten
        if (store.snapshot == null || (written != null && !written.isStillIn(current))) {
            store.snapshot = current
        }
        access.write(target)
        store.lastWritten = target
    }

    /**
     * Ends the rule-driven state. Writes the snapshot back only if [restore] is set and the
     * settings still hold what the rule wrote. Returns true if something was restored.
     */
    fun end(restore: Boolean): Boolean {
        val snapshot = store.snapshot ?: return false
        val written = store.lastWritten
        val untouched = written == null || written.isStillIn(access.read())
        val restored = restore && untouched
        if (restored) access.write(snapshot)
        clear()
        return restored
    }

    fun applyManual(target: SystemRotation) {
        access.write(target)
        clear()
    }

    private fun clear() {
        store.snapshot = null
        store.lastWritten = null
    }
}
