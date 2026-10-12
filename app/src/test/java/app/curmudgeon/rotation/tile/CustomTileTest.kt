// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.view.Surface
import app.curmudgeon.rotation.orientation.HomeChoice
import app.curmudgeon.rotation.orientation.SystemRotation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTileTest {
    @Test
    fun lockTargetsAreInForceOnlyWithTheirOwnDirection() {
        assertTrue(TileTarget.LOCK_RIGHT.isInForce(HomeChoice.LOCKED, Surface.ROTATION_90))
        assertFalse(TileTarget.LOCK_RIGHT.isInForce(HomeChoice.LOCKED, Surface.ROTATION_0))
        assertFalse(TileTarget.LOCK_RIGHT.isInForce(HomeChoice.FOUR_WAY, Surface.ROTATION_90))
    }

    @Test
    fun otherTargetsIgnoreTheArrow() {
        assertTrue(TileTarget.LANDSCAPE_FLIP.isInForce(HomeChoice.LANDSCAPE_FLIP, Surface.ROTATION_270))
        assertFalse(TileTarget.AUTO.isInForce(null, Surface.ROTATION_0))
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        val tile = CustomTile(3, "Reading", TileIcon.BOOK, TilePress.LOCK_LEFT, TilePress.CYCLE_LOCKS, TileBack(null, 1, SystemRotation(false, 2)))
        assertEquals(tile, CustomTile.fromJson(JSONObject(tile.toJson().toString())))
        val plain = tile.copy(doubleTap = null, back = TileBack(HomeChoice.FOUR_WAY, 0, null))
        assertEquals(plain, CustomTile.fromJson(JSONObject(plain.toJson().toString())))
    }

    @Test
    fun tilesSavedBeforeDoubleTapReadBackWithNone() {
        val tile = CustomTile.fromJson(JSONObject("""{"slot":1,"name":"x","icon":"star","target":"landscape_flip"}"""))
        assertEquals(TilePress.LANDSCAPE_FLIP, tile?.tap)
        assertNull(tile?.doubleTap)
    }

    @Test
    fun rotateChoicePressesAreStoredAsTheirTarget() {
        TilePress.entries.forEach { press -> press.target?.let { assertEquals(it.value, press.value) } }
        assertEquals(TileTarget.entries.toSet(), TilePress.entries.mapNotNull { it.target }.toSet())
    }

    @Test
    fun actionPressesMatchTheBuiltInActions() {
        val actions = TileAction.entries.map { it.value }
        listOf(TilePress.FLIP, TilePress.TOGGLE_ORIENTATION, TilePress.CYCLE, TilePress.CYCLE_WITH_REVERSE, TilePress.TOGGLE_AUTO_ROTATE, TilePress.OPEN_APP)
            .forEach { assertTrue(it.value, it.value in actions) }
    }

    @Test
    fun targetsAreBothTapsRotateChoices() {
        assertEquals(listOf(TileTarget.AUTO, TileTarget.LOCK_TOP), CustomTile(0, "x", TileIcon.STAR, TilePress.AUTO, TilePress.LOCK_TOP).targets)
        assertEquals(listOf(TileTarget.AUTO), CustomTile(0, "x", TileIcon.STAR, TilePress.FLIP, TilePress.AUTO).targets)
    }

    @Test
    fun unreadableTilesAreDropped() {
        assertNull(CustomTile.fromJson(JSONObject("""{"slot":2,"name":"x","icon":"star","target":"from_the_future"}""")))
        assertNull(CustomTile.fromJson(JSONObject("""{"slot":${CustomTileSlots.COUNT},"name":"x","icon":"star","target":"auto"}""")))
        assertNull(CustomTile.fromJson(JSONObject("""{"slot":2,"name":"x","icon":"star","target":"auto","double":"from_the_future"}""")))
    }

    @Test
    fun freeSlotIsTheFirstUnused() {
        assertEquals(1, CustomTileSlots.free(listOf(0, 2)))
        assertNull(CustomTileSlots.free((0 until CustomTileSlots.COUNT).toList()))
    }
}
