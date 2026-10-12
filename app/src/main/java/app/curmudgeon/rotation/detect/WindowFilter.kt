// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

/**
 * Decides which foreground changes count as leaving the app in front. Only windows that sit on top of an app without
 * replacing it are skipped (system UI: notification shade, volume and power menus; framework dialogs and the share
 * sheet), plus packages the user listed, so the rule of the app underneath stays in effect instead of flickering off
 * and on. The keyboard window is not an activity, so it never gets here ([ActivityResolver]).
 *
 * Every full screen counts, ours, a keyboard app's settings and the launcher included: leaving an app is leaving it.
 * Whether the launcher ends a rule or a tile is decided later, per "Ignore launcher" setting ([ForegroundTracker]).
 */
class WindowFilter(private val userIgnoredPackages: Set<String>) {
    fun accepts(packageName: String): Boolean =
        packageName !in ALWAYS_IGNORED && packageName !in userIgnoredPackages

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
