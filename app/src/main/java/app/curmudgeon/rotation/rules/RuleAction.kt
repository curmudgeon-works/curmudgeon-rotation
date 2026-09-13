// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

/**
 * What a rule does while its app (or screen) is in front.
 *
 * [id] is the stable identifier written to preferences and export files; never change it.
 */
enum class RuleAction(val id: String, val mechanism: Mechanism) {
    /** Rule is kept (with its screen list) but does nothing, as if there was no rule. */
    FOLLOW_SYSTEM("follow_system", Mechanism.NONE),
    AUTO_ROTATE_ON("auto_on", Mechanism.SYSTEM_SETTING),
    /** Turns auto-rotate off, locking the display in whatever rotation it currently has. */
    AUTO_ROTATE_OFF("auto_off", Mechanism.SYSTEM_SETTING),
    PORTRAIT("portrait", Mechanism.SYSTEM_SETTING),
    LANDSCAPE("landscape", Mechanism.SYSTEM_SETTING),
    REVERSE_LANDSCAPE("reverse_landscape", Mechanism.SYSTEM_SETTING),
    FORCE_LANDSCAPE("force_landscape", Mechanism.OVERLAY),
    FORCE_PORTRAIT("force_portrait", Mechanism.OVERLAY),
    FORCE_AUTO("force_auto", Mechanism.OVERLAY);

    enum class Mechanism {
        NONE,
        /** Writes Settings.System rotation values; needs WRITE_SETTINGS. Works for apps that follow the system. */
        SYSTEM_SETTING,
        /** Adds an invisible window requesting an orientation; overrides apps that lock their own orientation. */
        OVERLAY,
    }

    companion object {
        fun fromId(id: String): RuleAction? = entries.firstOrNull { it.id == id }
    }
}
