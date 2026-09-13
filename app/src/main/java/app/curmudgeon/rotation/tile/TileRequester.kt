// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.tile

import android.app.StatusBarManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.settings.Prefs

object TileRequester {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Shows the system "Add tile?" prompt (Android 13+). [onDone] runs on the main thread with whether the tile is now added. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun request(context: Context, onDone: (Boolean) -> Unit) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            RotationTileService.component(context),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_rotation_auto),
            context.mainExecutor,
        ) { result ->
            val added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            if (added) Prefs.tileAdded = true
            onDone(added)
        }
    }
}
