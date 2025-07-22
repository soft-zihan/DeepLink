package com.example.aggregatesearch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.adapters.IconAdapter
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.ui.recyclerview.IconItemTouchHelperCallback
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent

class GroupedSearchUrlAdapter(
    private val onItemClicked: (Any) -> Unit,
    private val onEditItemClicked: (Any) -> Unit,
    private val onAddUrlClicked: (UrlGroup) -> Unit,
    private val onIconStateChanged: (SearchUrl) -> Unit,
    private val onGroupCheckChanged: (UrlGroup, Boolean) -> Unit,
    private val onGroupExpandCollapse: (UrlGroup, Boolean) -> Unit,
    private val onIconOrderChanged: (List<SearchUrl>) -> Unit
) : ListAdapter<UrlGroup, GroupedSearchUrlAdapter.GroupWithIconsViewHolder>(GroupDiffCallback()) {

    private var isEditMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupWithIconsViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_group_with_icons, parent, false)
        return GroupWithIconsViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupWithIconsViewHolder, position: Int) {
        val group = getItem(position)
        holder.bind(group, group.urls, isEditMode)
    }

    override fun submitList(list: List<UrlGroup>?) {
        // The ListAdapter will handle the list update and diffing.
        // No need to manage iconStates here anymore.
        super.submitList(list)
    }

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            // Notify the whole list to reflect edit mode changes in all view holders
            notifyDataSetChanged() // This is one of the few acceptable uses of notifyDataSetChanged
        }
    }

    fun isInEditMode() = isEditMode

    inner class GroupWithIconsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var lastClickedIconId: Long = -1
        private var lastClickTime: Long = 0L

        private val groupContainer: View = itemView.findViewById(R.id.groupContainer)
        private val groupHeader: View = itemView.findViewById(R.id.groupHeader)
        private val textViewGroupName: TextView = itemView.findViewById(R.id.textViewGroupName)
        private val clickableGroupArea: View = itemView.findViewById(R.id.clickableGroupArea)
        private val imageViewExpandCollapse: ImageView = itemView.findViewById(R.id.imageViewExpandCollapse)
        private val buttonEditGroup: ImageButton = itemView.findViewById(R.id.buttonEditGroup)
        private val buttonAddUrlToGroup: ImageButton = itemView.findViewById(R.id.buttonAddUrlToGroup)
        private val iconsRecyclerView: RecyclerView = itemView.findViewById(R.id.iconsContainer)
        private var itemTouchHelper: ItemTouchHelper? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(group: UrlGroup, urls: List<SearchUrl>, isEditMode: Boolean) {
            textViewGroupName.text = group.name
            buttonEditGroup.visibility = if (isEditMode) View.VISIBLE else View.GONE
            buttonAddUrlToGroup.visibility = if (isEditMode) View.VISIBLE else View.GONE

            applyGroupColorToHeader(groupContainer, group.color)

            imageViewExpandCollapse.rotation = if (group.isExpanded) 180f else 0f
            imageViewExpandCollapse.setOnClickListener {
                group.isExpanded = !group.isExpanded
                onGroupExpandCollapse(group, group.isExpanded)
                notifyItemChanged(bindingAdapterPosition)
            }

            var lastHeaderClickTime = 0L
            var pendingHeaderClickRunnable: Runnable? = null
            clickableGroupArea.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHeaderClickTime < 300) {
                    pendingHeaderClickRunnable?.let { runnable ->
                        clickableGroupArea.removeCallbacks(runnable)
                        pendingHeaderClickRunnable = null
                    }
                    onItemClicked(group)
                } else {
                    pendingHeaderClickRunnable = Runnable {
                        val isGroupSelected = group.urls.isNotEmpty() && group.urls.all { it.isEnabled }
                        val newSelectedState = !isGroupSelected
                        onGroupCheckChanged(group, newSelectedState)
                        pendingHeaderClickRunnable = null
                    }
                    clickableGroupArea.postDelayed(pendingHeaderClickRunnable, 300)
                }
                lastHeaderClickTime = currentTime
            }

            buttonEditGroup.setOnClickListener { onEditItemClicked(group) }
            buttonAddUrlToGroup.setOnClickListener { onAddUrlClicked(group) }

            if (group.isExpanded) {
                iconsRecyclerView.visibility = View.VISIBLE
                setupIconsRecyclerView(urls, isEditMode)
            } else {
                iconsRecyclerView.visibility = View.GONE
            }
        }

        private fun setupIconsRecyclerView(urls: List<SearchUrl>, isEditMode: Boolean) {
            val iconAdapter: IconAdapter

            if (iconsRecyclerView.adapter == null) {
                iconAdapter = IconAdapter(
                    onIconStateChanged = { searchUrl -> onIconStateChanged(searchUrl) },
                    onIconClick = { searchUrl ->
                        if (this@GroupedSearchUrlAdapter.isEditMode) {
                            onEditItemClicked(searchUrl)
                            return@IconAdapter
                        }

                        val currentTime = System.currentTimeMillis()
                        val isSameIcon = lastClickedIconId == searchUrl.id

                        if (isSameIcon && currentTime - lastClickTime < 300) {
                            onItemClicked(searchUrl)
                        }

                        lastClickedIconId = searchUrl.id
                        lastClickTime = currentTime
                    },
                    onEditIconClick = { searchUrl -> onEditItemClicked(searchUrl) },
                    itemTouchHelperProvider = { itemTouchHelper!! }
                )
                iconsRecyclerView.adapter = iconAdapter
                iconsRecyclerView.layoutManager = FlexboxLayoutManager(itemView.context).apply {
                    justifyContent = JustifyContent.FLEX_START
                }
            } else {
                iconAdapter = iconsRecyclerView.adapter as IconAdapter
            }

            if (itemTouchHelper == null) {
                val callback = IconItemTouchHelperCallback { orderedUrls ->
                    onIconOrderChanged(orderedUrls)
                }
                itemTouchHelper = ItemTouchHelper(callback)
                itemTouchHelper?.attachToRecyclerView(iconsRecyclerView)
            }

            iconAdapter.setEditMode(isEditMode)
            iconAdapter.submitList(urls)
        }

        private fun applyGroupColorToHeader(view: View, groupColorHex: String?) {
            val textViewGroupName: TextView = view.findViewById(R.id.textViewGroupName)
            val alpha = getGroupBackgroundAlpha(view.context)

            if (groupColorHex != null && groupColorHex.isNotEmpty()) {
                try {
                    val color = Color.parseColor(groupColorHex)
                    view.setBackgroundColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
                    textViewGroupName.setTextColor(getDefaultTextColor(view.context))
                } catch (e: IllegalArgumentException) {
                    applyDefaultBackground(view)
                }
            } else {
                applyDefaultBackground(view)
            }
        }

        private fun applyDefaultBackground(view: View) {
            val textViewGroupName: TextView = view.findViewById(R.id.textViewGroupName)
            val typedValue = TypedValue()
            val fallbackColor = Color.parseColor("#A9A9A9")
            val alpha = getGroupBackgroundAlpha(view.context)

            try {
                view.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
                val themeBackgroundColor = typedValue.data
                if (isColorTooLight(themeBackgroundColor)) {
                    view.setBackgroundColor(Color.argb(alpha, Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor)))
                } else {
                    view.setBackgroundColor(Color.argb(alpha, Color.red(themeBackgroundColor), Color.green(themeBackgroundColor), Color.blue(themeBackgroundColor)))
                }
            } catch (e: Exception) {
                view.setBackgroundColor(Color.argb(alpha, Color.red(fallbackColor), Color.green(fallbackColor), Color.blue(fallbackColor)))
            }
            textViewGroupName.setTextColor(getDefaultTextColor(view.context))
        }

        private fun getGroupBackgroundAlpha(context: Context): Int {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val alphaPercent = prefs.getInt("pref_group_background_alpha", 100)
            return (alphaPercent * 255 / 100).coerceIn(0, 255)
        }

        private fun getDefaultTextColor(context: android.content.Context): Int {
            val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
        }

        private fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5
        }

        private fun isColorTooLight(color: Int): Boolean {
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return luminance > 0.8
        }
    }
}

class GroupDiffCallback : DiffUtil.ItemCallback<UrlGroup>() {
    override fun areItemsTheSame(oldItem: UrlGroup, newItem: UrlGroup): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: UrlGroup, newItem: UrlGroup): Boolean {
        return oldItem == newItem
    }
}
