package com.example.aggregatesearch.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R

class TabAdapter(
    private var tabs: List<BrowserTab> = emptyList(),
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabDoubleClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    private var selectedTabId: String? = null
    private val clickTimes = mutableMapOf<String, Long>()
    private val DOUBLE_CLICK_TIME_DELTA = 300L

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tabTitle: TextView = itemView.findViewById(R.id.tabTitle)
        val closeButton: ImageButton = itemView.findViewById(R.id.closeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browser_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.tabTitle.text = tab.title

        // 设置选中状态
        holder.itemView.isSelected = tab.id == selectedTabId

        // 主标签不显示关闭按钮
        holder.closeButton.visibility = if (tab.isMainTab) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            val lastClickTime = clickTimes[tab.id] ?: 0L

            if (currentTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA && !tab.isMainTab) {
                // 双击删除（非主标签）
                onTabDoubleClick(tab)
            } else {
                // 单击切换
                onTabClick(tab)
            }

            clickTimes[tab.id] = currentTime
        }

        holder.closeButton.setOnClickListener {
            if (!tab.isMainTab) {
                onTabClose(tab)
            }
        }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<BrowserTab>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    fun setSelectedTab(tabId: String) {
        selectedTabId = tabId
        notifyDataSetChanged()
    }
}
