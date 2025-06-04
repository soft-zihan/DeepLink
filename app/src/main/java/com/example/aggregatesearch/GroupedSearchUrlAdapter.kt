package com.example.aggregatesearch

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup

class GroupedSearchUrlAdapter(
    private val onItemClicked: (Any) -> Unit,
    private val onDeleteItemClicked: (Any) -> Unit,
    private val onEditItemClicked: (Any) -> Unit, // 新增编辑项目的回调
    private val onUrlCheckChanged: (SearchUrl, Boolean) -> Unit,
    private val onGroupCheckChanged: (UrlGroup, Boolean) -> Unit,
    private val onGroupExpandCollapse: (UrlGroup, Boolean) -> Unit,
    private val onItemMoveRequested: (fromPosition: Int, toPosition: Int) -> Unit,
    private val getUrlsForGroup: (groupId: Long) -> List<SearchUrl> // 添加一个新的函数参数用于获取组内所有URL
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_URL = 1
    }

    private var items: List<Any> = listOf()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is UrlGroup -> VIEW_TYPE_GROUP
            is SearchUrl -> VIEW_TYPE_URL
            else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_GROUP -> GroupViewHolder(inflater.inflate(R.layout.item_url_group, parent, false))
            VIEW_TYPE_URL -> UrlViewHolder(inflater.inflate(R.layout.item_search_url, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is UrlGroup -> (holder as GroupViewHolder).bind(item)
            is SearchUrl -> (holder as UrlViewHolder).bind(item)
        }
    }

    override fun getItemCount() = items.size

    fun getItemAt(position: Int): Any = items[position]
    fun getItems(): List<Any> = items

    fun getGroupOriginalIndex(group: UrlGroup): Int {
        var groupCount = 0
        for(i in items.indices){
            if(items[i] is UrlGroup){
                if(items[i] == group) return groupCount
                groupCount++
            }
        }
        return -1
    }


    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewGroupName: TextView = itemView.findViewById(R.id.textViewGroupName)
        private val checkboxGroupSelect: CheckBox = itemView.findViewById(R.id.checkboxGroupSelect)
        private val imageViewExpandCollapse: ImageView = itemView.findViewById(R.id.imageViewExpandCollapse)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle_group)
        private val buttonDeleteGroup: ImageButton = itemView.findViewById(R.id.buttonDeleteGroup)
        private val buttonEditGroup: ImageButton = itemView.findViewById(R.id.buttonEditGroup)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(group: UrlGroup) {
            textViewGroupName.text = group.name
            checkboxGroupSelect.setOnCheckedChangeListener(null)

            // 改为从所有URL列表中查找该分组下的所有URL，而不是仅检查当前显示的items列表
            val allUrlsInGroup = getUrlsForGroup(group.id)
            val isGroupSelected = allUrlsInGroup.isNotEmpty() && allUrlsInGroup.all { it.isEnabled }
            checkboxGroupSelect.isChecked = isGroupSelected

            checkboxGroupSelect.setOnCheckedChangeListener { _, isChecked ->
                onGroupCheckChanged(group, isChecked)
            }
            imageViewExpandCollapse.setImageResource(
                if (group.isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )
            imageViewExpandCollapse.setOnClickListener {
                onGroupExpandCollapse(group, !group.isExpanded)
            }

            // 设置整个项目的点击事件，用于触发分组内所有链接
            itemView.setOnClickListener { onItemClicked(group) }

            // 长按拖动功能，使整个分组项长按可拖动
            itemView.setOnLongClickListener {
                itemView.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }

            // 为删除按钮设置点击事件
            buttonDeleteGroup.setOnClickListener {
                onDeleteItemClicked(group)
            }

            // 为编辑按钮设置点击事件
            buttonEditGroup.setOnClickListener {
                onEditItemClicked(group)
            }
        }
    }

    inner class UrlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUrlName: TextView = itemView.findViewById(R.id.textViewUrlName)
        private val textViewUrlPattern: TextView = itemView.findViewById(R.id.textViewUrlPattern)
        private val buttonDeleteUrl: ImageButton = itemView.findViewById(R.id.buttonDeleteUrl)
        private val buttonEditUrl: ImageButton = itemView.findViewById(R.id.buttonEditUrl)
        private val checkboxUrlEnabled: CheckBox = itemView.findViewById(R.id.checkboxUrlEnabled)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle_url)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(searchUrl: SearchUrl) {
            textViewUrlName.text = searchUrl.name
            textViewUrlPattern.text = searchUrl.urlPattern
            checkboxUrlEnabled.setOnCheckedChangeListener(null)
            checkboxUrlEnabled.isChecked = searchUrl.isEnabled
            checkboxUrlEnabled.setOnCheckedChangeListener { _, isChecked ->
                onUrlCheckChanged(searchUrl, isChecked)
            }
            buttonDeleteUrl.setOnClickListener { onDeleteItemClicked(searchUrl) }
            buttonEditUrl.setOnClickListener { onEditItemClicked(searchUrl) }
            itemView.setOnClickListener { onItemClicked(searchUrl) }

            // 长按拖动功能，让整个链接项长按可拖动
            itemView.setOnLongClickListener {
                itemView.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
        }
    }
}
