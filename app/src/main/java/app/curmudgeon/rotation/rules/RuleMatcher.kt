// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

object RuleMatcher {
    /**
     * Returns the action to apply while [activity] of [packageName] is in front, or null when no
     * rule applies. A [RuleAction.FOLLOW_SYSTEM] rule and a rule limited to other screens both
     * count as "no rule", so any previously forced rotation ends.
     */
    fun match(rules: Collection<AppRule>, packageName: String, activity: String): RuleAction? {
        val rule = rules.firstOrNull { it.packageName == packageName } ?: return null
        if (!rule.appliesTo(activity)) return null
        return rule.action.takeUnless { it == RuleAction.FOLLOW_SYSTEM }
    }
}
