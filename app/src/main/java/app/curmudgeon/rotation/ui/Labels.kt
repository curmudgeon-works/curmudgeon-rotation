// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.orientation.OrientationMode
import app.curmudgeon.rotation.rules.RuleAction

@StringRes
fun OrientationMode.labelRes(): Int = when (this) {
    OrientationMode.OFF -> R.string.mode_off
    OrientationMode.AUTO -> R.string.mode_auto
    OrientationMode.PORTRAIT -> R.string.mode_portrait
    OrientationMode.LANDSCAPE -> R.string.mode_landscape
    OrientationMode.REVERSE_LANDSCAPE -> R.string.mode_reverse_landscape
}

@DrawableRes
fun OrientationMode.iconRes(): Int = when (this) {
    OrientationMode.OFF -> R.drawable.ic_rotation_off
    OrientationMode.AUTO -> R.drawable.ic_rotation_auto
    OrientationMode.PORTRAIT -> R.drawable.ic_rotation_portrait
    OrientationMode.LANDSCAPE -> R.drawable.ic_rotation_landscape
    OrientationMode.REVERSE_LANDSCAPE -> R.drawable.ic_rotation_reverse_landscape
}

@StringRes
fun RuleAction.labelRes(): Int = when (this) {
    RuleAction.FOLLOW_SYSTEM -> R.string.action_follow_system
    RuleAction.AUTO_ROTATE_ON -> R.string.action_auto_on
    RuleAction.AUTO_ROTATE_OFF -> R.string.action_auto_off
    RuleAction.PORTRAIT -> R.string.action_portrait
    RuleAction.LANDSCAPE -> R.string.action_landscape
    RuleAction.REVERSE_LANDSCAPE -> R.string.action_reverse_landscape
    RuleAction.FORCE_LANDSCAPE -> R.string.action_force_landscape
    RuleAction.FORCE_PORTRAIT -> R.string.action_force_portrait
    RuleAction.FORCE_AUTO -> R.string.action_force_auto
}

@StringRes
fun RuleAction.descriptionRes(): Int = when (this) {
    RuleAction.FOLLOW_SYSTEM -> R.string.action_follow_system_desc
    RuleAction.AUTO_ROTATE_ON -> R.string.action_auto_on_desc
    RuleAction.AUTO_ROTATE_OFF -> R.string.action_auto_off_desc
    RuleAction.PORTRAIT -> R.string.action_portrait_desc
    RuleAction.LANDSCAPE -> R.string.action_landscape_desc
    RuleAction.REVERSE_LANDSCAPE -> R.string.action_reverse_landscape_desc
    RuleAction.FORCE_LANDSCAPE -> R.string.action_force_landscape_desc
    RuleAction.FORCE_PORTRAIT -> R.string.action_force_portrait_desc
    RuleAction.FORCE_AUTO -> R.string.action_force_auto_desc
}
