// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ActivityTileEditorBinding
import app.curmudgeon.rotation.tile.CustomTile
import app.curmudgeon.rotation.tile.CustomTileService
import app.curmudgeon.rotation.tile.CustomTileStore
import app.curmudgeon.rotation.tile.TileIcon
import app.curmudgeon.rotation.tile.TileRequester
import app.curmudgeon.rotation.tile.TilePresence
import app.curmudgeon.rotation.tile.TilePress
import app.curmudgeon.rotation.tile.drawableRes
import app.curmudgeon.rotation.tile.labelRes

/**
 * Makes or edits the custom tile in one Quick Settings slot. Same rows as a built-in tile's screen (Add to Quick
 * Settings, single tap, double tap, long press), then its name and icon.
 */
class TileEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTileEditorBinding
    private var slot = 0
    private var existing: CustomTile? = null
    private var icon = TileIcon.ROTATION
    private var tap = TilePress.LANDSCAPE_FLIP
    private var doubleTap: TilePress? = null
    private val iconViews = mutableMapOf<TileIcon, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slot = intent.getIntExtra(EXTRA_SLOT, -1).takeIf { it >= 0 } ?: run { finish(); return }
        binding = ActivityTileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = true)

        existing = CustomTileStore.find(slot)
        if (savedInstanceState == null) {
            existing?.let {
                binding.nameField.setText(it.name)
                icon = it.icon
                tap = it.tap
                doubleTap = it.doubleTap
            }
        } else {
            icon = TileIcon.of(savedInstanceState.getString(STATE_ICON)) ?: icon
            tap = TilePress.of(savedInstanceState.getString(STATE_TAP)) ?: tap
            doubleTap = TilePress.of(savedInstanceState.getString(STATE_DOUBLE_TAP))
        }
        if (existing == null) supportActionBar?.setTitle(R.string.custom_tile_new_title)

        buildIconGrid()
        binding.addedRow.setOnClickListener { setAdded(!binding.addedBox.isChecked) }
        binding.tapRow.setOnClickListener {
            pickPress(R.string.tile_single_tap, TilePress.entries, tap) { tap = it ?: tap }
        }
        binding.doubleTapRow.setOnClickListener {
            pickPress(R.string.tile_double_tap, listOf(null) + TilePress.entries, doubleTap) { doubleTap = it }
        }
        binding.longPressValue.setText(R.string.tile_long_press_summary)
        renderPresses()
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { if (save()) finish() }
    }

    override fun onResume() {
        super.onResume()
        // the tile may have been added or taken out in Quick Settings meanwhile
        binding.addedBox.isChecked = existing != null && TilePresence.isAdded(TilePresence.customId(slot))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ICON, icon.value)
        outState.putString(STATE_TAP, tap.value)
        outState.putString(STATE_DOUBLE_TAP, doubleTap?.value)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tile_editor, menu)
        menu.findItem(R.id.action_delete).isVisible = existing != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_delete) return super.onOptionsItemSelected(item)
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_tile_delete)
            .setMessage(R.string.custom_tile_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.custom_tile_delete_confirm) { _, _ ->
                CustomTileStore.remove(slot)
                finish()
            }
            .show()
        return true
    }

    /** The icons in rows of [ICONS_PER_ROW]; the chosen one sits on an accent-tinted background. */
    private fun buildIconGrid() {
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
        val padding = size / 4
        TileIcon.entries.chunked(ICONS_PER_ROW).forEach { rowIcons ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            binding.iconGrid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size))
            rowIcons.forEach { option ->
                val view = ImageView(this).apply {
                    setImageResource(option.drawableRes())
                    setPadding(padding, padding, padding, padding)
                    contentDescription = option.value
                    setOnClickListener {
                        icon = option
                        renderIcons()
                    }
                }
                row.addView(view, LinearLayout.LayoutParams(0, size, 1f))
                iconViews[option] = view
            }
            // keep a short last row's icons the same width as the others
            repeat(ICONS_PER_ROW - rowIcons.size) { row.addView(android.view.View(this), LinearLayout.LayoutParams(0, size, 1f)) }
        }
        renderIcons()
    }

    private fun renderIcons() {
        val accent = ContextCompat.getColor(this, R.color.accent)
        val normal = ContextCompat.getColor(this, R.color.text)
        iconViews.forEach { (option, view) ->
            val chosen = option == icon
            view.imageTintList = ColorStateList.valueOf(if (chosen) accent else normal)
            view.alpha = if (chosen) 1f else 0.6f
            view.isSelected = chosen
            view.setBackgroundResource(if (chosen) R.drawable.bg_card else 0)
        }
    }

    private fun renderPresses() {
        binding.tapValue.setText(tap.labelRes())
        binding.doubleTapValue.setText(doubleTap?.labelRes() ?: R.string.tile_press_none)
    }

    /** One-choice list of [options] (null = Nothing), each with a line on what it does; [onPicked] gets the choice. */
    private fun pickPress(title: Int, options: List<TilePress?>, current: TilePress?, onPicked: (TilePress?) -> Unit) {
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val items = options.map { option ->
            if (option == null) getString(R.string.tile_press_none)
            else SpannableStringBuilder(getString(option.labelRes())).append('\n')
                .inSpans(ForegroundColorSpan(secondary), RelativeSizeSpan(0.85f)) { append(getString(option.summaryRes())) }
        }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(items, options.indexOf(current)) { dialog, which ->
                onPicked(options[which])
                renderPresses()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Ticked: saves the tile (it must exist to be added) and asks Android to add it (Android 13+; older versions get
     * the how-to screen). Unticked: takes it out of Quick Settings, keeping the tile.
     */
    private fun setAdded(added: Boolean) {
        if (!added) {
            if (existing != null) CustomTileStore.removeFromQuickSettings(slot)
            binding.addedBox.isChecked = false
            return
        }
        if (!save()) return
        if (!TileRequester.isSupported) {
            startActivity(SetupActivity.intent(this, SetupTopic.TILE))
            return
        }
        TileRequester.request(this, CustomTileService.component(this, slot), binding.nameField.text.toString().trim(), icon.drawableRes()) { done ->
            if (done) TilePresence.onAdded(TilePresence.customId(slot))
            binding.addedBox.isChecked = done
        }
    }

    /** Stores the tile as the screen shows it; false (with a hint) when it has no name. */
    private fun save(): Boolean {
        val name = binding.nameField.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.custom_tile_no_name, Toast.LENGTH_LONG).show()
            return false
        }
        // changed Rotate choices start fresh: the remembered "back" belonged to the old ones
        val tile = CustomTile(slot, name, icon, tap, doubleTap)
        val back = existing?.takeIf { it.targets == tile.targets }?.back
        CustomTileStore.put(tile.copy(back = back))
        existing = CustomTileStore.find(slot)
        invalidateOptionsMenu()
        return true
    }

    companion object {
        private const val EXTRA_SLOT = "slot"
        private const val STATE_ICON = "icon"
        private const val STATE_TAP = "tap"
        private const val STATE_DOUBLE_TAP = "double_tap"
        private const val ICONS_PER_ROW = 7

        fun intent(context: Context, slot: Int): Intent =
            Intent(context, TileEditorActivity::class.java).putExtra(EXTRA_SLOT, slot)
    }
}

/** One line on what a press does, under its name in the editor's list. */
private fun TilePress.summaryRes(): Int = when (this) {
    TilePress.AUTO -> R.string.rotate_auto_summary
    TilePress.FOUR_WAY -> R.string.rotate_four_way_summary
    TilePress.LOCK_TOP, TilePress.LOCK_RIGHT, TilePress.LOCK_BOTTOM, TilePress.LOCK_LEFT -> R.string.custom_tile_lock_summary
    TilePress.PORTRAIT_FLIP -> R.string.rotate_portrait_flip_summary
    TilePress.LANDSCAPE_FLIP -> R.string.rotate_landscape_flip_summary
    TilePress.FLIP -> R.string.tile_action_flip
    TilePress.LANDSCAPE_LOCK -> R.string.lock_tap_landscape
    TilePress.PORTRAIT_LOCK -> R.string.tile_portrait_summary
    TilePress.CYCLE_LOCKS -> R.string.lock_tap_cycle
    TilePress.TOGGLE_ORIENTATION -> R.string.tile_action_toggle_orientation
    TilePress.CYCLE -> R.string.tile_action_cycle
    TilePress.CYCLE_WITH_REVERSE -> R.string.tile_action_cycle_with_reverse
    TilePress.TOGGLE_AUTO_ROTATE -> R.string.tile_action_toggle_auto_rotate
    TilePress.OPEN_APP -> R.string.tile_action_open_app
}
