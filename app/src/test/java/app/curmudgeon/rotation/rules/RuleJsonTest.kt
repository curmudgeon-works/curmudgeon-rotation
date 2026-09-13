// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleJsonTest {
    @Test
    fun roundTripKeepsEveryActionAndScreenList() {
        val rules = RuleAction.entries.mapIndexed { i, action ->
            AppRule("com.example.app$i", action, if (i % 2 == 0) emptySet() else setOf("com.example.app$i.A", "com.example.app$i.B"))
        }
        assertEquals(rules.toSet(), RuleJson.fromJson(RuleJson.toJson(rules)).toSet())
    }

    @Test
    fun emptyRuleListRoundTrips() {
        assertEquals(emptyList<AppRule>(), RuleJson.fromJson(RuleJson.toJson(emptyList())))
    }

    @Test
    fun unknownActionsAndBrokenEntriesAreSkipped() {
        val json = """
            {"format":"curmudgeon-rotation-rules","version":2,"rules":[
              {"package":"com.a","action":"landscape"},
              {"package":"com.b","action":"teleport"},
              {"action":"portrait"},
              "garbage",
              {"package":"com.c","action":"force_auto","activities":["com.c.X",""]}
            ]}
        """.trimIndent()
        assertEquals(
            listOf(AppRule("com.a", RuleAction.LANDSCAPE), AppRule("com.c", RuleAction.FORCE_AUTO, setOf("com.c.X"))),
            RuleJson.fromJson(json),
        )
    }

    @Test
    fun laterDuplicatePackageWins() {
        val json = """{"format":"curmudgeon-rotation-rules","rules":[
            {"package":"com.a","action":"landscape"},{"package":"com.a","action":"portrait"}]}"""
        assertEquals(listOf(AppRule("com.a", RuleAction.PORTRAIT)), RuleJson.fromJson(json))
    }

    @Test(expected = JSONException::class)
    fun foreignJsonIsRejected() {
        RuleJson.fromJson("""{"rules":[]}""")
    }

    @Test
    fun actionIdsAreStable() {
        // ids are persisted and exported; changing one silently drops users' rules
        assertEquals(
            listOf("follow_system", "auto_on", "auto_off", "portrait", "landscape", "reverse_landscape", "force_landscape", "force_portrait", "force_auto"),
            RuleAction.entries.map { it.id },
        )
    }
}
