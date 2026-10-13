// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.settings.Prefs

object TileRequester {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Shows the system "Add tile?" prompt (Android 13+). [onDone] runs on the main thread with whether the tile is now added. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun request(context: Context, onDone: (Boolean) -> Unit) =
        request(context, RotationTileService.component(context), context.getString(R.string.tile_label), R.drawable.ic_rotation_auto) { added ->
            if (added) Prefs.tileAdded = true
            onDone(added)
        }

    /** The same prompt for any of our tiles: [component] with [label] and [icon]. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun request(context: Context, component: ComponentName, label: String, @DrawableRes icon: Int, onDone: (Boolean) -> Unit) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            component,
            label,
            Icon.createWithResource(context, icon),
            context.mainExecutor,
        ) { result ->
            onDone(result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
        }
    }

    /** Before asking Android to add a tile: once added, only the Quick Settings panel can take it out. */
    fun confirmAdding(context: Context, onAdd: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.add_tile_confirm_title)
            .setMessage(R.string.add_tile_confirm_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_tile_confirm) { _, _ -> onAdd() }
            .show()
    }

    /** Android has no way for an app to take a tile out of Quick Settings: says how to do it by hand. */
    fun explainRemoving(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.remove_tile_title)
            .setMessage(R.string.remove_tile_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
