// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.view.Surface
import app.curmudgeon.rotation.orientation.HomeChoice
import app.curmudgeon.rotation.orientation.SystemRotation
import org.json.JSONObject

/**
 * What a custom tile's tap picks under "Rotate": one of the Rotate choices, with Lock orientation split into its four
 * directions (a tile can't show an arrow to turn). Tapping the tile again goes back to what was in force before.
 */
enum class TileTarget(val value: String, val choice: HomeChoice, val arrow: Int? = null) {
    AUTO("auto", HomeChoice.AUTO),
    FOUR_WAY("four_way", HomeChoice.FOUR_WAY),
    LOCK_TOP("lock_top", HomeChoice.LOCKED, Surface.ROTATION_0),
    LOCK_RIGHT("lock_right", HomeChoice.LOCKED, Surface.ROTATION_90),
    LOCK_BOTTOM("lock_bottom", HomeChoice.LOCKED, Surface.ROTATION_180),
    LOCK_LEFT("lock_left", HomeChoice.LOCKED, Surface.ROTATION_270),
    PORTRAIT_FLIP("portrait_flip", HomeChoice.PORTRAIT_FLIP),
    LANDSCAPE_FLIP("landscape_flip", HomeChoice.LANDSCAPE_FLIP);

    /** Whether this is what's in force, given the Rotate choice shown as selected and where the Lock arrow points. */
    fun isInForce(selected: HomeChoice?, lockArrow: Int): Boolean = selected == choice && (arrow == null || arrow == lockArrow)

    companion object {
        fun of(value: String?): TileTarget? = entries.firstOrNull { it.value == value }
    }
}

/**
 * What a custom tile's single or double tap does: a Rotate choice ([target], on until tapped again, which goes back to
 * what was in force before), or one of the built-in tiles' actions, which do their own thing each tap. [value] is
 * stored; a target's value is its [TileTarget.value], so tiles saved before double-tap existed read back unchanged.
 */
enum class TilePress(val value: String, val target: TileTarget? = null) {
    AUTO("auto", TileTarget.AUTO),
    FOUR_WAY("four_way", TileTarget.FOUR_WAY),
    LOCK_TOP("lock_top", TileTarget.LOCK_TOP),
    LOCK_RIGHT("lock_right", TileTarget.LOCK_RIGHT),
    LOCK_BOTTOM("lock_bottom", TileTarget.LOCK_BOTTOM),
    LOCK_LEFT("lock_left", TileTarget.LOCK_LEFT),
    PORTRAIT_FLIP("portrait_flip", TileTarget.PORTRAIT_FLIP),
    LANDSCAPE_FLIP("landscape_flip", TileTarget.LANDSCAPE_FLIP),

    /** The Rotate tile's tap. */
    FLIP("flip"),

    /** The Lock tile's tap and the Rotate tile's double-tap. */
    LANDSCAPE_LOCK("landscape_lock"),

    /** The Portrait tile's tap. */
    PORTRAIT_LOCK("portrait_lock"),

    /** Taps cycle the four locked orientations, the fifth releases (was a Lock tile choice). */
    CYCLE_LOCKS("cycle_locks"),

    /** The Rotate tile's other old choices ([TileAction]). */
    TOGGLE_ORIENTATION("toggle_orientation"),
    CYCLE("cycle"),
    CYCLE_WITH_REVERSE("cycle_with_reverse"),
    TOGGLE_AUTO_ROTATE("toggle_auto_rotate"),
    OPEN_APP("open_app");

    companion object {
        fun of(value: String?): TilePress? = entries.firstOrNull { it.value == value }
    }
}

/** The icons a custom tile can wear: bundled monochrome drawables, named by [value] in storage. */
enum class TileIcon(val value: String) {
    ROTATION("rotation"), AUTO("auto"), LOCKED("locked"), PORTRAIT("portrait"), LANDSCAPE("landscape"),
    REVERSE_LANDSCAPE("reverse_landscape"), PHONE("phone"), PLAY("play"), GAME("game"), BOOK("book"),
    CAMERA("camera"), CAR("car"), BED("bed"), STAR("star");

    companion object {
        fun of(value: String?): TileIcon? = entries.firstOrNull { it.value == value }
    }
}

/**
 * What to go back to when a custom tile is tapped off: the Rotate choice that was in force (with the Lock arrow), or
 * the system default with the settings it had ([system]).
 */
data class TileBack(val choice: HomeChoice?, val arrow: Int, val system: SystemRotation?) {
    fun toJson(): JSONObject = JSONObject().apply {
        choice?.let { put("choice", it.value) }
        put("arrow", arrow)
        system?.let {
            put("auto", it.autoRotate)
            put("user", it.userRotation)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TileBack = TileBack(
            HomeChoice.of(json.optString("choice", "").ifEmpty { null }),
            json.optInt("arrow", 0).coerceIn(0, 3),
            if (json.has("auto")) SystemRotation(json.getBoolean("auto"), json.optInt("user", 0)) else null,
        )
    }
}

/** A tile the user made: lives in Quick Settings slot [slot] (see [CustomTileSlots]). */
data class CustomTile(
    val slot: Int,
    val name: String,
    val icon: TileIcon,
    val tap: TilePress,
    /** Null: no double-tap, so a tap runs at once instead of waiting for a second one. */
    val doubleTap: TilePress? = null,
    /** Set while one of this tile's Rotate choices is in force: where tapping it off goes. */
    val back: TileBack? = null,
) {
    /** The Rotate choices this tile's taps pick. */
    val targets: List<TileTarget> get() = listOfNotNull(tap.target, doubleTap?.target)

    fun toJson(): JSONObject = JSONObject().apply {
        put("slot", slot)
        put("name", name)
        put("icon", icon.value)
        put("target", tap.value)
        doubleTap?.let { put("double", it.value) }
        back?.let { put("back", it.toJson()) }
    }

    companion object {
        /** Null for anything unreadable (an unknown target from a newer version, say). */
        fun fromJson(json: JSONObject): CustomTile? {
            val slot = json.optInt("slot", -1).takeIf { it in 0 until CustomTileSlots.COUNT } ?: return null
            val tap = TilePress.of(json.optString("target")) ?: return null
            val doubleTap = if (json.has("double")) TilePress.of(json.optString("double")) ?: return null else null
            return CustomTile(
                slot,
                json.optString("name"),
                TileIcon.of(json.optString("icon")) ?: TileIcon.ROTATION,
                tap,
                doubleTap,
                json.optJSONObject("back")?.let(TileBack::fromJson),
            )
        }
    }
}

object CustomTileSlots {
    /** Quick Settings tiles must be declared in the manifest: this many slots exist, each off until used. */
    const val COUNT = 8

    /** The first slot not in [used], or null when all are taken. */
    fun free(used: Collection<Int>): Int? = (0 until COUNT).firstOrNull { it !in used }
}
