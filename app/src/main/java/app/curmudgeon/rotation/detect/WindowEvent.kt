// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.detect

/** An activity that came to the front. */
data class WindowEvent(val packageName: String, val activity: String)
