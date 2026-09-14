// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

/** Pure rule for whether a remembered manual mode still describes the current system setting. */
object ManualModeResolver {
    /**
     * [system] is derived from the rotation settings alone, which cannot tell OFF from PORTRAIT (both are
     * "auto-rotate off, rotation 0"); the remembered [manual] choice breaks that tie. Any other mismatch means
     * rotation was changed outside this app, so the system wins.
     */
    fun consistent(manual: OrientationMode, system: OrientationMode): Boolean =
        manual == system || (manual == OrientationMode.PORTRAIT && system == OrientationMode.OFF)
}
