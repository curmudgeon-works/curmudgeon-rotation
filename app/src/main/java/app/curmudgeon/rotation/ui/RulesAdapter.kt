// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.curmudgeon.rotation.R
import app.curmudgeon.rotation.databinding.ItemRuleBinding
import app.curmudgeon.rotation.rules.AppRule

class RulesAdapter(private val onClick: (AppRule) -> Unit) : ListAdapter<AppRule, RulesAdapter.Holder>(Diff) {
    class Holder(val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rule = getItem(position)
        val context = holder.itemView.context
        val app = AppInfoLoader.load(context, rule.packageName)
        holder.binding.ruleIcon.setImageDrawable(app.icon ?: AppCompatResources.getDrawable(context, R.drawable.ic_rotation_setup))
        holder.binding.ruleLabel.text = app.label
        val screens = if (rule.activities.isEmpty()) {
            context.getString(R.string.rule_all_screens)
        } else {
            context.resources.getQuantityString(R.plurals.rule_screen_count, rule.activities.size, rule.activities.size)
        }
        val parts = listOfNotNull(
            context.getString(rule.action.labelRes()),
            screens,
            context.getString(R.string.rule_not_installed).takeIf { !app.installed },
        )
        holder.binding.ruleSummary.text = parts.joinToString(" · ")
        holder.itemView.setOnClickListener { onClick(rule) }
    }

    private object Diff : DiffUtil.ItemCallback<AppRule>() {
        override fun areItemsTheSame(oldItem: AppRule, newItem: AppRule) = oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppRule, newItem: AppRule) = oldItem == newItem
    }
}
