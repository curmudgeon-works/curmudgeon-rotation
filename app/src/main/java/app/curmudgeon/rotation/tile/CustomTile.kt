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
    val target: TileTarget,
    /** Set while this tile's tap is in force: where tapping it again goes. */
    val back: TileBack? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("slot", slot)
        put("name", name)
        put("icon", icon.value)
        put("target", target.value)
        back?.let { put("back", it.toJson()) }
    }

    companion object {
        /** Null for anything unreadable (an unknown target from a newer version, say). */
        fun fromJson(json: JSONObject): CustomTile? {
            val slot = json.optInt("slot", -1).takeIf { it in 0 until CustomTileSlots.COUNT } ?: return null
            val target = TileTarget.of(json.optString("target")) ?: return null
            return CustomTile(
                slot,
                json.optString("name"),
                TileIcon.of(json.optString("icon")) ?: TileIcon.ROTATION,
                target,
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
