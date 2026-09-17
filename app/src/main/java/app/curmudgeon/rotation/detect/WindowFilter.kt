// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

/**
 * Decides which foreground changes may switch rules. Transient windows (keyboard, system UI and
 * notification shade, system dialogs, our own screens, optionally the launcher) are skipped, so the
 * rule of the app underneath stays in effect instead of flickering off and on.
 *
 * Our own package is skipped because every screen of ours is a detour from the app the rules are
 * about: opening settings, or the screen a tile long-press opens.
 *
 * The launcher is ignored by default because recents and app switching pass through it; launchers
 * usually lock their own orientation anyway.
 */
class WindowFilter(
    private val ownPackage: String,
    private val userIgnoredPackages: Set<String>,
    private val inputMethodPackages: Set<String>,
    private val launcherPackages: Set<String>,
    private val ignoreLauncher: Boolean,
) {
    fun accepts(packageName: String): Boolean =
        packageName != ownPackage &&
            packageName !in ALWAYS_IGNORED &&
            packageName !in userIgnoredPackages &&
            packageName !in inputMethodPackages &&
            !(ignoreLauncher && packageName in launcherPackages)

    companion object {
        /** System UI (shade, volume, power menu) and the framework ("android": share sheet, resolver). */
        val ALWAYS_IGNORED = setOf("com.android.systemui", "android")

        /**
         * Accessibility window events also fire for dialogs, popups and toasts. When the package
         * manager can't tell us whether a class is an activity (package not visible to us), classes
         * from framework namespaces are assumed not to be.
         */
        fun looksLikeFrameworkWindow(className: String): Boolean =
            FRAMEWORK_PREFIXES.any { className.startsWith(it) }

        private val FRAMEWORK_PREFIXES = listOf(
            "android.widget.", "android.view.", "android.app.", "android.inputmethodservice.",
            "androidx.appcompat.app.", "com.google.android.material.",
        )
    }
}
