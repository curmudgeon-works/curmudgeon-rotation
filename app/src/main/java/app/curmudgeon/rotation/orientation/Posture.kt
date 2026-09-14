// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.orientation

/** How the phone is physically held, from the orientation sensor. */
enum class Posture {
    PORTRAIT, LANDSCAPE, UNKNOWN;

    companion object {
        private const val MARGIN = 30

        fun of(landscape: Boolean): Posture = if (landscape) LANDSCAPE else PORTRAIT

        /**
         * [degrees] as reported by OrientationEventListener (0–359, or negative when the phone lies flat).
         * Within [MARGIN] of upright or upside down is portrait, within [MARGIN] of either side is landscape;
         * in between is unknown, so a tilt halfway doesn't count as either.
         */
        fun of(degrees: Int): Posture {
            if (degrees < 0) return UNKNOWN
            val d = degrees % 180
            return when {
                d <= MARGIN || d >= 180 - MARGIN -> PORTRAIT
                d in 90 - MARGIN..90 + MARGIN -> LANDSCAPE
                else -> UNKNOWN
            }
        }
    }
}

/** Reports when the phone has been held in [target] for [stableMs], so a wobble on the way doesn't count. */
class TurnTracker(private val target: Posture, private val stableMs: Long = 300) {
    private var since: Long? = null

    /** Feeds one sensor sample; true once [target] has held for [stableMs]. */
    fun onSample(posture: Posture, nowMs: Long): Boolean {
        if (posture != target) {
            since = null
            return false
        }
        val start = since ?: nowMs.also { since = it }
        return nowMs - start >= stableMs
    }
}
