package com.example.aggregatesearch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R
import com.example.aggregatesearch.utils.AppPackageManager.AppInfo

/**
 * 应用列表适配器
 */
class AppAdapter(private val onAppClick: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(itemView: View, private val onAppClick: (AppInfo) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        private val textViewAppName: TextView = itemView.findViewById(R.id.textViewAppName)
        private val textViewPackageName: TextView = itemView.findViewById(R.id.textViewPackageName)
        private lateinit var currentApp: AppInfo

        init {
            itemView.setOnClickListener {
                onAppClick(currentApp)
            }
        }

        fun bind(appInfo: AppInfo) {
            currentApp = appInfo
            textViewAppName.text = appInfo.appName
            textViewPackageName.text = appInfo.packageName
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
