// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowFilterTest {
    private val filter = WindowFilter(userIgnoredPackages = setOf("com.facebook.orca"))

    @Test
    fun regularAppsPass() {
        assertTrue(filter.accepts("com.google.android.youtube"))
    }

    @Test
    fun windowsOnTopOfAnAppAreIgnored() {
        assertFalse(filter.accepts("com.android.systemui"))
        assertFalse(filter.accepts("android"))
        assertFalse(filter.accepts("com.facebook.orca"))
    }

    /** Leaving an app is leaving it: our own screens, a keyboard app's settings and the launcher all count. */
    @Test
    fun fullScreensThatReplaceTheAppCount() {
        assertTrue(filter.accepts("app.curmudgeon.rotation"))
        assertTrue(filter.accepts("app.curmudgeon.keyboard"))
        assertTrue(filter.accepts("com.teslacoilsw.launcher"))
        assertTrue(filter.accepts("com.google.android.apps.nexuslauncher"))
        assertTrue(filter.accepts("com.android.settings"))
    }

    @Test
    fun frameworkWindowClassesAreNotActivities() {
        assertTrue(WindowFilter.looksLikeFrameworkWindow("android.app.AlertDialog"))
        assertTrue(WindowFilter.looksLikeFrameworkWindow("android.widget.PopupWindow\$PopupDecorView"))
        assertFalse(WindowFilter.looksLikeFrameworkWindow("com.wbd.stream.PlayerActivity"))
    }
}
