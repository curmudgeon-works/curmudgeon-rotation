// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.rules

/**
 * One rule per app. When [activities] is empty the rule covers every screen of [packageName];
 * otherwise only those fully qualified activity class names.
 */
data class AppRule(
    val packageName: String,
    val action: RuleAction,
    val activities: Set<String> = emptySet(),
) {
    fun appliesTo(activity: String): Boolean = activities.isEmpty() || activity in activities
}

/**
 * Turns user input into a fully qualified activity class name: surrounding whitespace is dropped
 * and a leading "." is resolved against [packageName], as in manifests.
 */
fun normalizeActivityName(packageName: String, input: String): String {
    val name = input.trim()
    return if (name.startsWith(".")) packageName + name else name
}
