// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.RadioButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.curmudgeon.rotation.R

/**
 * A tile row on the "Quick Settings tiles" screen with an "in Quick Settings" radio button. The button only shows
 * [added]: tapping it calls [onRadio], which asks Android and sets [added] from the answer. Built-in rows do the
 * same on a tap anywhere; custom tile rows open the tile editor instead.
 */
class QsTilePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {
    var onRadio: () -> Unit = {}

    var added = false
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    init {
        widgetLayoutResource = R.layout.pref_qs_radio
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.qsRadio) as RadioButton).apply {
            isChecked = added
            setOnClickListener {
                isChecked = added // a radio button checks itself on a tap; it shows only what Android says
                onRadio()
            }
        }
    }
}
