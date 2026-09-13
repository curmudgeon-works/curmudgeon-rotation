// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleMatcherTest {
    private val youtube = AppRule("com.google.android.youtube", RuleAction.AUTO_ROTATE_ON)
    private val hbo = AppRule("com.wbd.stream", RuleAction.FORCE_LANDSCAPE, setOf("com.wbd.stream.PlayerActivity"))
    private val paused = AppRule("com.example.paused", RuleAction.FOLLOW_SYSTEM)
    private val rules = listOf(youtube, hbo, paused)

    @Test
    fun packageRuleAppliesToEveryScreen() {
        assertEquals(RuleAction.AUTO_ROTATE_ON, RuleMatcher.match(rules, youtube.packageName, "com.google.android.apps.youtube.app.WatchWhileActivity"))
        assertEquals(RuleAction.AUTO_ROTATE_ON, RuleMatcher.match(rules, youtube.packageName, "AnyOtherActivity"))
    }

    @Test
    fun activityRuleAppliesOnlyToListedScreens() {
        assertEquals(RuleAction.FORCE_LANDSCAPE, RuleMatcher.match(rules, hbo.packageName, "com.wbd.stream.PlayerActivity"))
        assertNull(RuleMatcher.match(rules, hbo.packageName, "com.wbd.stream.HomeActivity"))
    }

    @Test
    fun activityOfAnotherPackageDoesNotMatch() {
        assertNull(RuleMatcher.match(rules, "com.other.app", "com.wbd.stream.PlayerActivity"))
    }

    @Test
    fun unknownPackageAndFollowSystemMeanNoRule() {
        assertNull(RuleMatcher.match(rules, "com.unknown", "com.unknown.Main"))
        assertNull(RuleMatcher.match(rules, paused.packageName, "com.example.paused.Main"))
    }

    @Test
    fun relativeActivityNamesResolveAgainstPackage() {
        assertEquals("com.wbd.stream.PlayerActivity", normalizeActivityName("com.wbd.stream", " .PlayerActivity "))
        assertEquals("org.other.Activity", normalizeActivityName("com.wbd.stream", "org.other.Activity"))
    }
}
