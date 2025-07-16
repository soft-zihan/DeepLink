package com.example.aggregatesearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.utils.IconLoader

class SearchUrlAdapter(
    private val onItemClicked: (SearchUrl) -> Unit,
    private val onDeleteClicked: (SearchUrl) -> Unit,
    private val onCheckChanged: (SearchUrl, Boolean) -> Unit,
    private val onDragInitiated: (RecyclerView.ViewHolder) -> Unit,
    val onItemMoveRequested: (fromPosition: Int, toPosition: Int) -> Unit
) : ListAdapter<SearchUrl, SearchUrlAdapter.SearchUrlViewHolder>(SEARCH_URL_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchUrlViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_url, parent, false)
        return SearchUrlViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchUrlViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current, onDragInitiated)
    }

    inner class SearchUrlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUrlName: TextView = itemView.findViewById(R.id.textViewUrlName)
        private val textViewUrlPattern: TextView = itemView.findViewById(R.id.textViewUrlPattern)
        private val checkboxUrlEnabled: CheckBox = itemView.findViewById(R.id.checkboxUrlEnabled)
        private val imageViewUrlIcon: ImageView = itemView.findViewById(R.id.imageViewUrlIcon)

        fun bind(searchUrl: SearchUrl, dragInitiator: (RecyclerView.ViewHolder) -> Unit) {
            textViewUrlName.text = searchUrl.name
            textViewUrlPattern.text = searchUrl.urlPattern

            // 加载图标（使用新的支持文字图标的方法）
            IconLoader.loadIcon(itemView.context, searchUrl, imageViewUrlIcon)

            // 重置监听器，避免重用视图时触发事件
            checkboxUrlEnabled.setOnCheckedChangeListener(null)
            checkboxUrlEnabled.isChecked = searchUrl.isEnabled

            // 为复选框添加点击监听器
            checkboxUrlEnabled.setOnCheckedChangeListener { _, isChecked ->
                // 只有当状态真正变化时才触发回调
                if (isChecked != searchUrl.isEnabled) {
                    onCheckChanged(searchUrl, isChecked)
                }
            }

            // 为图标添加单独的点击事件，模拟复选框点击
            imageViewUrlIcon.setOnClickListener {
                // 直接修改复选框状态，这会触发上面的 OnCheckedChangeListener
                val newState = !checkboxUrlEnabled.isChecked
                checkboxUrlEnabled.isChecked = newState
                // 为确保状态更新，也显式调用回调
                onCheckChanged(searchUrl, newState)
            }

            itemView.setOnClickListener {
                onItemClicked(searchUrl)
            }

            itemView.setOnLongClickListener {
                dragInitiator(this)
                true
            }
        }
    }

    companion object {
        private val SEARCH_URL_COMPARATOR = object : DiffUtil.ItemCallback<SearchUrl>() {
            override fun areItemsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
                return oldItem == newItem
            }
        }
    }
}
