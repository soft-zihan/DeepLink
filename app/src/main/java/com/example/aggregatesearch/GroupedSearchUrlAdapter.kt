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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup

class GroupedSearchUrlAdapter(
    private val onItemClicked: (Any) -> Unit,
    private val onDeleteItemClicked: (Any) -> Unit,
    private val onEditItemClicked: (Any) -> Unit, // 新增编辑项目的回调
    private val onAddUrlClicked: (UrlGroup) -> Unit,
    private val onUrlCheckChanged: (SearchUrl, Boolean) -> Unit,
    private val onGroupCheckChanged: (UrlGroup, Boolean) -> Unit,
    private val onGroupExpandCollapse: (UrlGroup, Boolean) -> Unit,
    private val onItemMoveRequested: (fromPosition: Int, toPosition: Int) -> Unit,
    private val getUrlsForGroup: (groupId: Long) -> List<SearchUrl> // 添加一个新的函数参数用于获取组内所有URL
) : ListAdapter<Any, RecyclerView.ViewHolder>(GroupedItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_URL = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
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
        when (val item = getItem(position)) {
            is UrlGroup -> (holder as GroupViewHolder).bind(item)
            is SearchUrl -> (holder as UrlViewHolder).bind(item)
        }
    }

    fun getItemAt(position: Int): Any = getItem(position)
    fun getItems(): List<Any> = currentList

    fun getGroupOriginalIndex(group: UrlGroup): Int {
        var groupCount = 0
        for(i in currentList.indices){
            if(currentList[i] is UrlGroup){
                if(currentList[i] == group) return groupCount
                groupCount++
            }
        }
        return -1
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewGroupName: TextView = itemView.findViewById(R.id.textViewGroupName)
        private val clickableGroupArea: View = itemView.findViewById(R.id.clickableGroupArea)
        private val checkboxGroupSelect: CheckBox = itemView.findViewById(R.id.checkboxGroupSelect)
        private val imageViewExpandCollapse: ImageView = itemView.findViewById(R.id.imageViewExpandCollapse)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle_group)
        private val buttonDeleteGroup: ImageButton = itemView.findViewById(R.id.buttonDeleteGroup)
        private val buttonEditGroup: ImageButton = itemView.findViewById(R.id.buttonEditGroup)
        private val buttonAddUrlToGroup: ImageButton = itemView.findViewById(R.id.buttonAddUrlToGroup)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(group: UrlGroup) {
            textViewGroupName.text = group.name
            checkboxGroupSelect.setOnCheckedChangeListener(null)

            val allUrlsInGroup = getUrlsForGroup(group.id)
            val isGroupSelected = allUrlsInGroup.isNotEmpty() && allUrlsInGroup.all { it.isEnabled }
            checkboxGroupSelect.isChecked = isGroupSelected

            checkboxGroupSelect.setOnCheckedChangeListener { _, isChecked ->
                onGroupCheckChanged(group, isChecked)
            }
            imageViewExpandCollapse.rotation = if (group.isExpanded) 180f else 0f
            imageViewExpandCollapse.setOnClickListener {
                imageViewExpandCollapse.animate()
                    .rotationBy(180f)
                    .setDuration(300)
                    .start()
                onGroupExpandCollapse(group, !group.isExpanded)
            }
            clickableGroupArea.setOnClickListener { onItemClicked(group) }
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener {
                itemView.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
            buttonDeleteGroup.setOnClickListener {
                onDeleteItemClicked(group)
            }
            buttonEditGroup.setOnClickListener {
                onEditItemClicked(group)
            }
            buttonAddUrlToGroup.setOnClickListener {
                onAddUrlClicked(group)
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
            textViewUrlPattern.visibility = View.GONE
            checkboxUrlEnabled.setOnCheckedChangeListener(null)
            checkboxUrlEnabled.isChecked = searchUrl.isEnabled
            checkboxUrlEnabled.setOnCheckedChangeListener { _, isChecked ->
                onUrlCheckChanged(searchUrl, isChecked)
            }
            buttonDeleteUrl.setOnClickListener { onDeleteItemClicked(searchUrl) }
            buttonEditUrl.setOnClickListener { onEditItemClicked(searchUrl) }
            itemView.setOnClickListener { onItemClicked(searchUrl) }
            itemView.setOnLongClickListener {
                itemView.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
        }
    }
}

class GroupedItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is UrlGroup && newItem is UrlGroup -> oldItem.id == newItem.id
            oldItem is SearchUrl && newItem is SearchUrl -> oldItem.id == newItem.id
            else -> oldItem == newItem
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is UrlGroup && newItem is UrlGroup -> oldItem == newItem
            oldItem is SearchUrl && newItem is SearchUrl -> oldItem == newItem
            else -> oldItem == newItem
        }
    }
}
