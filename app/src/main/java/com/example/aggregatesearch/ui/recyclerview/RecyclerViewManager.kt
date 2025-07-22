/**
 * RecyclerView管理器
 * 负责处理主列表的所有交互逻辑
 * 包括：适配器设置、拖拽排序、项目移动、编辑模式切换等
 */
package com.example.aggregatesearch.ui.recyclerview

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.GroupedSearchUrlAdapter
import com.example.aggregatesearch.R
import com.example.aggregatesearch.SearchViewModel
import com.example.aggregatesearch.UrlLauncher
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class RecyclerViewManager(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val searchViewModel: SearchViewModel,
    private val onEditItemClicked: (Any) -> Unit,
    private val onAddUrlClicked: (UrlGroup) -> Unit,
    private val onIconStateChanged: (SearchUrl) -> Unit,
    private val getCurrentSearchQuery: () -> String,
    private val onIconOrderChanged: (List<SearchUrl>) -> Unit
) {

    private lateinit var groupedAdapter: GroupedSearchUrlAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var isEditMode = false

    fun setupRecyclerView() {
        groupedAdapter = GroupedSearchUrlAdapter(
            onItemClicked = { item ->
                val searchQuery = getCurrentSearchQuery()
                when (item) {
                    is SearchUrl -> {
                        UrlLauncher.launchSearchUrls(context, searchQuery, listOf(item))
                    }
                    is UrlGroup -> {
                        val enabledUrlsInGroup = item.urls.filter { it.isEnabled }
                        if (enabledUrlsInGroup.isNotEmpty()) {
                            UrlLauncher.launchSearchUrls(context, searchQuery, enabledUrlsInGroup)
                        } else {
                            Toast.makeText(context, "该分组内没有启用的链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onEditItemClicked = onEditItemClicked,
            onAddUrlClicked = onAddUrlClicked,
            onIconStateChanged = onIconStateChanged,
            onGroupCheckChanged = { group, isChecked ->
                searchViewModel.updateGroupSelected(group, isChecked)
            },
            onGroupExpandCollapse = { group, isExpanded ->
                searchViewModel.updateGroupExpanded(group, isExpanded)
            },
            onIconOrderChanged = onIconOrderChanged
        )
        binding.recyclerViewUrls.adapter = groupedAdapter

        setupItemTouchHelper()
    }

    private fun setupItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    val list = groupedAdapter.currentList.toMutableList()
                    val movedItem = list.removeAt(fromPosition)
                    list.add(toPosition, movedItem)
                    searchViewModel.updateGroupsOrder(list)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true // Re-enable long press drag for groups

            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewUrls)
        binding.recyclerViewUrls.setTag(R.id.item_touch_helper, itemTouchHelper)
    }

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        if (::groupedAdapter.isInitialized) {
            groupedAdapter.setEditMode(editMode)
        }
    }

    fun submitList(list: List<UrlGroup>) {
        if (::groupedAdapter.isInitialized) {
            groupedAdapter.submitList(list)
        }
    }

    fun refreshIconsForGroup(groupId: Long) {
        if (::groupedAdapter.isInitialized) {
            val position = groupedAdapter.currentList.indexOfFirst { it.id == groupId }
            if (position != -1) {
                groupedAdapter.notifyItemChanged(position)
            }
        }
    }

    fun getAdapter(): GroupedSearchUrlAdapter? {
        return if (::groupedAdapter.isInitialized) groupedAdapter else null
    }

    fun refreshList() {
        if (::groupedAdapter.isInitialized) {
            groupedAdapter.notifyDataSetChanged()
        }
    }

    fun notifyDataSetChanged() {
        if (::groupedAdapter.isInitialized) {
            groupedAdapter.notifyDataSetChanged()
        }
    }
}
