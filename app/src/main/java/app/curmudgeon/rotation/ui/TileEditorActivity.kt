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
import android.widget.RadioButton
import android.widget.RadioGroup
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
import app.curmudgeon.rotation.tile.TileTarget
import app.curmudgeon.rotation.tile.drawableRes
import app.curmudgeon.rotation.tile.labelRes

/** Makes or edits the custom tile in one Quick Settings slot: its name, icon and what a tap picks. */
class TileEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTileEditorBinding
    private var slot = 0
    private var existing: CustomTile? = null
    private var icon = TileIcon.ROTATION
    private var target = TileTarget.LANDSCAPE_FLIP
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
                target = it.target
            }
        } else {
            icon = TileIcon.of(savedInstanceState.getString(STATE_ICON)) ?: icon
            target = TileTarget.of(savedInstanceState.getString(STATE_TARGET)) ?: target
        }
        if (existing == null) supportActionBar?.setTitle(R.string.custom_tile_new_title)

        buildIconGrid()
        buildTargetOptions()
        binding.addToQuickSettings.setOnClickListener { save(addToQuickSettings = true) }
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save(addToQuickSettings = false) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ICON, icon.value)
        outState.putString(STATE_TARGET, target.value)
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

    private fun buildTargetOptions() {
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.option_padding)
        TileTarget.entries.forEach { option ->
            val text = SpannableStringBuilder(getString(option.labelRes())).append('\n')
                .inSpans(ForegroundColorSpan(secondary), RelativeSizeSpan(0.85f)) { append(getString(option.summaryRes())) }
            val button = RadioButton(this).apply {
                id = ViewGroup.generateViewId()
                this.text = text
                tag = option
                setPadding(paddingLeft, paddingTop + verticalPadding, paddingRight, paddingBottom + verticalPadding)
            }
            binding.targetGroup.addView(button, RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT))
            if (option == target) button.isChecked = true
        }
        binding.targetGroup.setOnCheckedChangeListener { group, checkedId ->
            target = group.findViewById<RadioButton>(checkedId).tag as TileTarget
        }
    }

    private fun save(addToQuickSettings: Boolean) {
        val name = binding.nameField.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.custom_tile_no_name, Toast.LENGTH_LONG).show()
            return
        }
        // a changed target starts fresh: the remembered "back" belonged to the old one
        val back = existing?.takeIf { it.target == target }?.back
        CustomTileStore.put(CustomTile(slot, name, icon, target, back))
        if (!addToQuickSettings) {
            finish()
            return
        }
        if (!TileRequester.isSupported) {
            startActivity(SetupActivity.intent(this, SetupTopic.TILE))
            finish()
            return
        }
        TileRequester.request(this, CustomTileService.component(this, slot), name, icon.drawableRes()) { finish() }
    }

    companion object {
        private const val EXTRA_SLOT = "slot"
        private const val STATE_ICON = "icon"
        private const val STATE_TARGET = "target"
        private const val ICONS_PER_ROW = 7

        fun intent(context: Context, slot: Int): Intent =
            Intent(context, TileEditorActivity::class.java).putExtra(EXTRA_SLOT, slot)
    }
}

/** One line on what a tap does, under the target's name in the editor. */
private fun TileTarget.summaryRes(): Int = when (this) {
    TileTarget.AUTO -> R.string.rotate_auto_summary
    TileTarget.FOUR_WAY -> R.string.rotate_four_way_summary
    TileTarget.LOCK_TOP, TileTarget.LOCK_RIGHT, TileTarget.LOCK_BOTTOM, TileTarget.LOCK_LEFT -> R.string.custom_tile_lock_summary
    TileTarget.PORTRAIT_FLIP -> R.string.rotate_portrait_flip_summary
    TileTarget.LANDSCAPE_FLIP -> R.string.rotate_landscape_flip_summary
}
