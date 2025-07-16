package com.example.aggregatesearch

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.utils.IconLoader

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

    // 添加编辑模式标志
    private var isEditMode = false
    // 增加一个Map来追踪待处理的状态变化，以解决UI更新延迟问题
    private val pendingStateChanges = mutableMapOf<Long, Boolean>()

    companion object {
        private const val VIEW_TYPE_GROUP = 0
        private const val VIEW_TYPE_URL = 1
        private const val VIEW_TYPE_URL_ICONS = 2 // 新增图标视图类型
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item is UrlGroup -> VIEW_TYPE_GROUP
            item is SearchUrl -> {
                val urlsInSameGroup = currentList.filterIsInstance<SearchUrl>().filter { it.groupId == item.groupId }
                if (urlsInSameGroup.firstOrNull()?.id == item.id) {
                    VIEW_TYPE_URL_ICONS // 每组中的第一个链接显示为图标容器
                } else {
                    -1 // 其他链接不显示（被合并到图标容器中）
                }
            }
            else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_GROUP -> GroupViewHolder(inflater.inflate(R.layout.item_url_group, parent, false))
            VIEW_TYPE_URL -> UrlViewHolder(inflater.inflate(R.layout.item_search_url, parent, false))
            VIEW_TYPE_URL_ICONS -> IconsContainerViewHolder(inflater.inflate(R.layout.item_url_icons_container, parent, false))
            else -> object : RecyclerView.ViewHolder(View(parent.context)) {} // 占位ViewHolder，不会显示
        }
    }

    // 重写submitList来清空待处理状态
    override fun submitList(list: List<Any>?) {
        super.submitList(list)
        // !! 重要修复：不再在这里清除状态，因为DiffUtil可能不会立即应用所有更改，
        // 导致在下一次绑定时状态丢失。状态将在RecyclerView布局完成后清除。
        // pendingStateChanges.clear()
        if (list != null) {
            android.util.Log.d("DeepLink", "新列表已提交。")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.doOnLayout {
            // 每次RecyclerView完成布局后，我们都可以安全地假设所有挂起的状态
            // 已经反映在UI上，此时可以清除缓存了。
            android.util.Log.d("DeepLink", "RecyclerView 布局完成，清除待处理状态。")
            pendingStateChanges.clear()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupViewHolder -> {
                val item = getItem(position) as UrlGroup
                holder.bind(item)

                holder.toggleEditButtons(isEditMode)
            }
            is IconsContainerViewHolder -> {
                val item = getItem(position) as SearchUrl
                val urls = getUrlsForGroup(item.groupId)
                if (urls.isNotEmpty()) {
                    holder.bind(urls, isEditMode)
                }
            }
            is UrlViewHolder -> {
                val item = getItem(position) as SearchUrl
                holder.bind(item)
            }
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

    /**
     * 用于外部调用更新分组勾选状态
     */
    fun updateGroupCheckedState(group: UrlGroup, isChecked: Boolean) {
        // 查找当前列表中的分组位置
        val position = currentList.indexOfFirst {
            it is UrlGroup && it.id == group.id
        }
        if (position != -1) {
            // 通知该位置的视图刷新
            notifyItemChanged(position, isChecked)
        }
    }

    // 设置编辑模式
    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    // 获取当前编辑模式状态
    fun isInEditMode() = isEditMode

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewGroupName: TextView = itemView.findViewById(R.id.textViewGroupName)
        private val clickableGroupArea: View = itemView.findViewById(R.id.clickableGroupArea)
        private val imageViewExpandCollapse: ImageView = itemView.findViewById(R.id.imageViewExpandCollapse)
        private val buttonEditGroup: ImageButton = itemView.findViewById(R.id.buttonEditGroup)
        private val buttonAddUrlToGroup: ImageButton = itemView.findViewById(R.id.buttonAddUrlToGroup)
        private val dragHandleGroup: ImageView = itemView.findViewById(R.id.drag_handle_group)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(group: UrlGroup) {
            textViewGroupName.text = group.name

            // Apply background color with default fallback to ensure group name is always visible
            val backgroundColorHex = group.color
            if (backgroundColorHex != null && backgroundColorHex.isNotEmpty()) {
                try {
                    val color = Color.parseColor(backgroundColorHex)
                    itemView.setBackgroundColor(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))) // 50% alpha

                    // 根据背景颜色深浅自动调整文本颜色，而不是固定为白色
                    if (isColorDark(color)) {
                        textViewGroupName.setTextColor(Color.WHITE)
                    } else {
                        textViewGroupName.setTextColor(Color.BLACK)
                    }
                } catch (e: IllegalArgumentException) {
                    // Invalid color string, fallback to theme color
                    applyDefaultBackground(itemView)
                    // 使用主题默认文本颜色
                    textViewGroupName.setTextColor(getDefaultTextColor(itemView.context))
                }
            } else {
                // No color set, use a distinguishable default
                applyDefaultBackground(itemView)
                // 使用主题默认文本颜色
                textViewGroupName.setTextColor(getDefaultTextColor(itemView.context))
            }

            imageViewExpandCollapse.rotation = if (group.isExpanded) 180f else 0f
            imageViewExpandCollapse.setOnClickListener {
                imageViewExpandCollapse.animate()
                    .rotationBy(180f)
                    .setDuration(300)
                    .start()
                onGroupExpandCollapse(group, !group.isExpanded)
            }

            // 修改分组点击行为：点击切换分组内所有项目的选中状态，并添加防抖动机制
            var lastClickTime = 0L
            var pendingClickRunnable: Runnable? = null
            clickableGroupArea.setOnClickListener {
                val currentTime = System.currentTimeMillis()

                // 如果是双击事件
                if (currentTime - lastClickTime < 300) {
                    // 取消待执行的单击事件
                    pendingClickRunnable?.let { runnable ->
                        clickableGroupArea.removeCallbacks(runnable)
                        pendingClickRunnable = null
                    }
                    // 执行双击事件：打开分组内已选中的图标
                    onItemClicked(group)
                } else {
                    // 如果是单击事件，延迟执行，以等待可能的第二次点击
                    pendingClickRunnable = Runnable {
                        // 切换分组内所有项目的选中状态
                        val allUrlsInGroup = getUrlsForGroup(group.id)
                        val isGroupSelected = allUrlsInGroup.isNotEmpty() && allUrlsInGroup.all { it.isEnabled }
                        onGroupCheckChanged(group, !isGroupSelected)
                        pendingClickRunnable = null
                    }
                    // 设置延迟执行
                    clickableGroupArea.postDelayed(pendingClickRunnable, 300)
                }
                lastClickTime = currentTime
            }

            // 设置拖动手柄点击事件 - 无需长按，直接点击手柄即可拖动
            dragHandleGroup.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // 找到ItemTouchHelper并启动拖动
                    val recyclerView = itemView.parent as? RecyclerView
                    val itemTouchHelper = recyclerView?.getTag(R.id.item_touch_helper) as? ItemTouchHelper
                    itemTouchHelper?.startDrag(this@GroupViewHolder)
                    true
                } else {
                    false
                }
            }

            // 设置分组长按拖动功能
            clickableGroupArea.setOnLongClickListener {
                // 找到ItemTouchHelper并启动拖动
                val recyclerView = itemView.parent as? RecyclerView
                val itemTouchHelper = recyclerView?.getTag(R.id.item_touch_helper) as? ItemTouchHelper
                itemTouchHelper?.startDrag(this@GroupViewHolder)
                true
            }

            buttonEditGroup.setOnClickListener {
                onEditItemClicked(group)
            }
            buttonAddUrlToGroup.setOnClickListener {
                onAddUrlClicked(group)
            }
        }
        private fun applyDefaultBackground(view: View) {
            // Apply theme's surface variant color or a light gray if that fails
            val typedValue = TypedValue()
            // 使用更深的灰色作为默认背景，确保白色文字可见性
            val fallbackColor = Color.parseColor("#A9A9A9") // 深灰色
            try {
                view.context.theme.resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true)
                val themeBackgroundColor = typedValue.data
                if (isColorTooLight(themeBackgroundColor)) {
                    view.setBackgroundColor(Color.argb(128, Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor)))
                } else {
                    view.setBackgroundColor(Color.argb(128, Color.red(themeBackgroundColor), Color.green(themeBackgroundColor), Color.blue(themeBackgroundColor)))
                }
            } catch (e: Exception) {
                view.setBackgroundColor(Color.argb(128, Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor)))
            }
        }

        private fun getDefaultTextColor(context: android.content.Context): Int {
            // 根据当前主题模式返回适当的文本颜色
            val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK

            // 如果是深色模式，返回白色；如果是浅色模式，返回黑色
            return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }

        private fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5 // Threshold for considering color as dark
        }

        private fun isColorTooLight(color: Int): Boolean {
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return luminance > 0.8 // Threshold for considering color too light for white text
        }

        fun toggleEditButtons(isEditMode: Boolean) {
            buttonEditGroup.visibility = if (isEditMode) View.VISIBLE else View.GONE
            buttonAddUrlToGroup.visibility = if (isEditMode) View.VISIBLE else View.GONE
        }
    }

    inner class UrlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUrlName: TextView = itemView.findViewById(R.id.textViewUrlName)
        private val textViewUrlPattern: TextView = itemView.findViewById(R.id.textViewUrlPattern)
        private val buttonEditUrl: ImageButton = itemView.findViewById(R.id.buttonEditUrl)
        private val checkboxUrlEnabled: CheckBox = itemView.findViewById(R.id.checkboxUrlEnabled)
        private val imageViewUrlIcon: ImageView = itemView.findViewById(R.id.imageViewUrlIcon)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle_url)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(searchUrl: SearchUrl) {
            try {
                textViewUrlName.text = searchUrl.name
                textViewUrlPattern.visibility = View.GONE

                // 使用更鲁棒的方式加载图标，直接使用 SearchUrl 对象
                IconLoader.loadIcon(itemView.context, searchUrl, imageViewUrlIcon)

                checkboxUrlEnabled.setOnCheckedChangeListener(null)
                checkboxUrlEnabled.isChecked = searchUrl.isEnabled

                // Find the group this URL belongs to and apply its color
                val parentGroup = findParentGroup(searchUrl.groupId)
                applyGroupColorToUrlItem(itemView, parentGroup?.color)

                checkboxUrlEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onUrlCheckChanged(searchUrl, isChecked)
                }

                buttonEditUrl.setOnClickListener {
                    try {
                        onEditItemClicked(searchUrl)
                    } catch (e: Exception) {
                        android.util.Log.e("GroupedSearchUrlAdapter", "Error in edit button click: ${e.message}")
                    }
                }

                itemView.setOnClickListener {
                    try {
                        onItemClicked(searchUrl)
                    } catch (e: Exception) {
                        android.util.Log.e("GroupedSearchUrlAdapter", "Error in item click: ${e.message}")
                    }
                }

                itemView.setOnLongClickListener {
                    itemView.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                // 保证拖动操作更稳定
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        itemView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupedSearchUrlAdapter", "Error binding URL item: ${e.message}")
                // 确保即使出错，UI 也不会完全崩溃
                textViewUrlName.text = searchUrl.name ?: "错误项"
            }
        }

        private fun applyGroupColorToUrlItem(view: View, groupColorHex: String?) {
            if (groupColorHex != null && groupColorHex.isNotEmpty()) {
                try {
                    val color = Color.parseColor(groupColorHex)
                    // Use a more subtle version of the group color for the URL items
                    view.setBackgroundColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))) // 20% alpha

                    if (isColorDark(color)) {
                        textViewUrlName.setTextColor(Color.WHITE)
                    } else {
                        textViewUrlName.setTextColor(Color.BLACK)
                    }
                } catch (e: Exception) {
                    // Default styling in case of error
                    view.setBackgroundColor(Color.TRANSPARENT)
                    // 使用系统默认文字颜色
                    applyDefaultTextColor(textViewUrlName)
                }
            } else {
                // No group color, use default
                view.setBackgroundColor(Color.TRANSPARENT)

                applyDefaultTextColor(textViewUrlName)
            }
        }

        // 判断颜色是否为深色
        private fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5 // Threshold for considering color as dark
        }

        // 设置默认文字颜色，从主题获取
        private fun applyDefaultTextColor(textView: TextView) {
            val typedValue = TypedValue()
            if (textView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
                textView.setTextColor(typedValue.data)
            } else {
                val nightModeFlags = textView.context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    textView.setTextColor(Color.WHITE)  // 夜间模式，使用白色
                } else {
                    textView.setTextColor(Color.BLACK)  // 日间模式，使用黑色
                }
            }
        }
    }

    inner class IconsContainerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconsContainer: com.google.android.flexbox.FlexboxLayout = itemView.findViewById(R.id.iconsContainer)
        // 将这些变量提取到类成员，使所有图标共享
        private var lastClickedIconId: Long = -1
        private var lastClickTime: Long = 0L
        private var pendingClickRunnable: Runnable? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(urls: List<SearchUrl>, isEditMode: Boolean) {
            // 清空现有视图
            iconsContainer.removeAllViews()

            // 获取分组颜色
            val groupId = urls.firstOrNull()?.groupId
            val parentGroup = groupId?.let { findParentGroup(it) }

            // 应用分组背景颜色到整个容器
            applyGroupColorToContainer(itemView, parentGroup?.color)

            // 设置容器本身的长按拖动（编辑模式下）
            itemView.setOnLongClickListener { view ->
                val recyclerView = itemView.parent as? RecyclerView
                val itemTouchHelper = recyclerView?.getTag(R.id.item_touch_helper) as? ItemTouchHelper
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && itemTouchHelper != null) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    itemTouchHelper.startDrag(this)
                    return@setOnLongClickListener true
                }
                false
            }

            // 清除之前可能存在的待执行点击事件
            pendingClickRunnable?.let { runnable ->
                itemView.removeCallbacks(runnable)
                pendingClickRunnable = null
            }

            // 动态添加图标视图
            urls.forEach { searchUrl ->
                val iconView = LayoutInflater.from(itemView.context).inflate(R.layout.item_url_icon, iconsContainer, false)
                val imageViewIcon: ImageView = iconView.findViewById(R.id.imageViewIcon)
                val textViewUrlName: TextView = iconView.findViewById(R.id.textViewUrlName)
                val iconEditButton: ImageView = iconView.findViewById(R.id.iconEditButton)

                // 使用更鲁棒的方式加载图标
                IconLoader.loadIcon(itemView.context, searchUrl, imageViewIcon)

                // 设置URL名称
                textViewUrlName.text = searchUrl.name

                // 确定当前图标的状态
                val isEnabled = pendingStateChanges[searchUrl.id] ?: searchUrl.isEnabled
                android.util.Log.d("DeepLink", "绑定图标 ${searchUrl.name}. 待定状态: ${pendingStateChanges[searchUrl.id]}, 原始状态: ${searchUrl.isEnabled}, 最终状态: $isEnabled")

                // 根据状态更新UI
                updateIconVisualState(imageViewIcon, isEnabled)

                // 编辑按钮只在编辑模式下显示
                iconEditButton.visibility = if (isEditMode) View.VISIBLE else View.GONE

                // 设置图标点击事件处理
                imageViewIcon.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    val isSameIcon = lastClickedIconId == searchUrl.id

                    if (isEditMode) {
                        // 编辑模式下，单击直接打开编辑页
                        onEditItemClicked(searchUrl)
                        return@setOnClickListener
                    }

                    // 处理双击检测
                    if (isSameIcon && currentTime - lastClickTime < 300) { // 双击相同图标
                        // 取消待执行的单击事件
                        pendingClickRunnable?.let { runnable ->
                            itemView.removeCallbacks(runnable)
                            pendingClickRunnable = null
                        }

                        // 双击时，直接打开链接，不切换选中状态
                        onItemClicked(searchUrl)
                        android.util.Log.d("DeepLink", "双击图标: ${searchUrl.name}, 打开链接")
                    } else { // 单击或点击不同图标
                        // 取消之前可能存在的待执行点击事件
                        pendingClickRunnable?.let { runnable ->
                            itemView.removeCallbacks(runnable)
                        }

                        // 创建新的点击处理
                        pendingClickRunnable = Runnable {
                            // 切换状态
                            val currentState = pendingStateChanges[searchUrl.id] ?: searchUrl.isEnabled
                            val newState = !currentState
                            android.util.Log.d("DeepLink", "单击图标: ${searchUrl.name}, 当前状态=$currentState, 新状态=$newState")

                            // 立即更新UI状态
                            pendingStateChanges[searchUrl.id] = newState
                            updateIconVisualState(imageViewIcon, newState)

                            // 通知ViewModel更新数据
                            onUrlCheckChanged(searchUrl, newState)
                            pendingClickRunnable = null
                        }

                        // 设置延迟执行，等待可能的第二次点击
                        itemView.postDelayed(pendingClickRunnable, 300)
                    }

                    // 更新最后点击记录
                    lastClickedIconId = searchUrl.id
                    lastClickTime = currentTime
                }

                // 设置图标长按事件
                iconView.setOnLongClickListener { view ->
                    if (isEditMode) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        startIconDrag()
                        true
                    } else false
                }

                // 设置编辑按钮点击事件
                iconEditButton.setOnClickListener {
                    if (isEditMode) {
                        onEditItemClicked(searchUrl)
                    }
                }

                iconsContainer.addView(iconView)
            }
        }

        private fun applyGroupColorToContainer(view: View, groupColorHex: String?) {
            if (groupColorHex != null && groupColorHex.isNotEmpty()) {
                try {
                    val color = Color.parseColor(groupColorHex)
                    view.setBackgroundColor(Color.argb(64, Color.red(color), Color.green(color), Color.blue(color))) // 25% alpha
                } catch (e: Exception) {
                    // 出错时使用透明背景
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
            } else {
                // 无组颜色时使用透明背景
                view.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // 启动图标排序的辅助方法
        private fun startIconDrag() {
            // 找到RecyclerView和ItemTouchHelper
            val recyclerView = itemView.parent as? RecyclerView
            val itemTouchHelper = recyclerView?.getTag(R.id.item_touch_helper) as? ItemTouchHelper

            // 启动拖动当前ViewHolder
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && itemTouchHelper != null) {
                itemTouchHelper.startDrag(this)
            }
        }

        // 新增辅助方法：更新图标的视觉状态
        private fun updateIconVisualState(imageView: ImageView, isSelected: Boolean) {
            try {
                // 设置选中状态
                imageView.isSelected = isSelected

                // 增加视觉反馈
                if (isSelected) {
                    // 选中时应用一个轻微的缩放和高亮效果
                    imageView.alpha = 1.0f
                    imageView.scaleX = 1.15f
                    imageView.scaleY = 1.15f
                    // 如果有背景，可以应用一个边框或半透明背景
                    imageView.background = androidx.core.content.ContextCompat.getDrawable(
                        imageView.context,
                        R.drawable.selected_icon_background
                    )
                } else {
                    // 非选中时恢复正常
                    imageView.alpha = 0.7f
                    imageView.scaleX = 1.0f
                    imageView.scaleY = 1.0f
                    imageView.background = null
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupedSearchUrlAdapter", "Error updating icon state: ${e.message}")
            }
        }
    }

    private fun findParentGroup(groupId: Long): UrlGroup? {
        return currentList.filterIsInstance<UrlGroup>().firstOrNull { it.id == groupId }
    }
}

class GroupedItemDiffCallback : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is UrlGroup && newItem is UrlGroup -> oldItem.id == newItem.id
            oldItem is SearchUrl && newItem is SearchUrl -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        return when {
            oldItem is UrlGroup && newItem is UrlGroup -> oldItem.name == newItem.name &&
                    oldItem.isExpanded == newItem.isExpanded &&
                    oldItem.orderIndex == newItem.orderIndex &&
                    oldItem.color == newItem.color
            oldItem is SearchUrl && newItem is SearchUrl -> oldItem.name == newItem.name &&
                    oldItem.urlPattern == newItem.urlPattern &&
                    oldItem.isEnabled == newItem.isEnabled &&
                    oldItem.orderIndex == newItem.orderIndex &&
                    oldItem.groupId == newItem.groupId &&
                    oldItem.packageName == newItem.packageName
            else -> false
        }
    }
}
