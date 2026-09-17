// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowFilterTest {
    private fun filter(ignoreLauncher: Boolean) = WindowFilter(
        ownPackage = "app.curmudgeon.rotation",
        userIgnoredPackages = setOf("com.facebook.orca"),
        inputMethodPackages = setOf("app.curmudgeon.keyboard"),
        launcherPackages = setOf("com.google.android.apps.nexuslauncher"),
        ignoreLauncher = ignoreLauncher,
    )

    @Test
    fun regularAppsPass() {
        assertTrue(filter(true).accepts("com.google.android.youtube"))
    }

    @Test
    fun transientWindowsAreIgnored() {
        val filter = filter(true)
        assertFalse(filter.accepts("com.android.systemui"))
        assertFalse(filter.accepts("android"))
        assertFalse(filter.accepts("app.curmudgeon.keyboard"))
        assertFalse(filter.accepts("com.facebook.orca"))
        assertFalse(filter.accepts("app.curmudgeon.rotation"))
    }

    @Test
    fun launcherIgnoreIsConfigurable() {
        assertFalse(filter(ignoreLauncher = true).accepts("com.google.android.apps.nexuslauncher"))
        assertTrue(filter(ignoreLauncher = false).accepts("com.google.android.apps.nexuslauncher"))
    }

    @Test
    fun frameworkWindowClassesAreNotActivities() {
        assertTrue(WindowFilter.looksLikeFrameworkWindow("android.app.AlertDialog"))
        assertTrue(WindowFilter.looksLikeFrameworkWindow("android.widget.PopupWindow\$PopupDecorView"))
        assertFalse(WindowFilter.looksLikeFrameworkWindow("com.wbd.stream.PlayerActivity"))
    }
}
