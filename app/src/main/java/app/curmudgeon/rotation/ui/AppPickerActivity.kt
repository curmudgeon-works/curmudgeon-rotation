// SPDX-License-Identifier: GPL-3.0-only
package app.curmudgeon.rotation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.curmudgeon.rotation.databinding.ActivityAppPickerBinding
import app.curmudgeon.rotation.databinding.ItemAppBinding
import app.curmudgeon.rotation.rules.RuleStore
import kotlin.concurrent.thread

/** Lists launchable apps; picking one opens the rule editor for it. */
class AppPickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppPickerBinding
    private var apps: List<AppInfo> = emptyList()
    private val adapter = AppAdapter { app ->
        startActivity(RuleEditorActivity.intent(this, app.packageName))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpChrome(binding.root, binding.toolbar, showUp = true)

        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter
        binding.search.doAfterTextChanged { applyFilter() }
        binding.showSystemApps.setOnCheckedChangeListener { _, _ -> applyFilter() }

        thread(name = "load-apps") {
            val loaded = AppInfoLoader.launchable(applicationContext)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = loaded
                binding.loading.isVisible = false
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val query = binding.search.text?.toString()?.trim().orEmpty()
        val showSystem = binding.showSystemApps.isChecked
        adapter.submitList(
            apps.filter { app ->
                (showSystem || !app.isSystem || RuleStore.find(app.packageName) != null) &&
                    (query.isEmpty() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
            },
        )
    }

    private class AppAdapter(private val onPick: (AppInfo) -> Unit) : ListAdapter<AppInfo, AppAdapter.Holder>(Diff) {
        class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = getItem(position)
            holder.binding.appIcon.setImageDrawable(app.icon)
            holder.binding.appLabel.text = app.label
            holder.binding.appPackage.text = app.packageName
            holder.itemView.setOnClickListener { onPick(app) }
        }

        private object Diff : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) = oldItem.packageName == newItem.packageName
            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) = oldItem.label == newItem.label
        }
    }
}
