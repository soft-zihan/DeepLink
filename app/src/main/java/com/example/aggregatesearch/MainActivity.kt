package com.example.aggregatesearch

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.databinding.ActivityMainBinding
import com.example.aggregatesearch.databinding.DialogAddGroupBinding
import com.example.aggregatesearch.databinding.DialogAddUrlBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((application as SearchApplication).repository)
    }
    private lateinit var groupedAdapter: GroupedSearchUrlAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val createBackupFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }

    private val openRestoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchFunction()
        setupAddUrlButton()
        setupAddGroupButton()

        lifecycleScope.launch {
            searchViewModel.groupedItems.collectLatest {
                groupedAdapter.submitList(it)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_backup -> {
                createBackupFile.launch("deeplink_search_backup.json")
                true
            }
            R.id.menu_restore -> {
                openRestoreFile.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val backupData = createBackupData()
                outputStream.write(backupData.toString(2).toByteArray())
                Toast.makeText(this, "备份成功", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importBackup(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = readTextFromStream(inputStream)
                restoreFromBackup(jsonString)
                Toast.makeText(this, "恢复成功，请重启应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createBackupData(): JSONObject {
        val rootObject = JSONObject()

        val groupsArray = JSONArray()
        searchViewModel.allGroups.value.forEach { group ->
            val groupObject = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("isExpanded", group.isExpanded)
                put("orderIndex", group.orderIndex)
            }
            groupsArray.put(groupObject)
        }
        rootObject.put("groups", groupsArray)

        val urlsArray = JSONArray()
        searchViewModel.allUrls.value?.forEach { url ->
            val urlObject = JSONObject().apply {
                put("id", url.id)
                put("name", url.name)
                put("urlPattern", url.urlPattern)
                put("isEnabled", url.isEnabled)
                put("orderIndex", url.orderIndex)
                put("groupId", url.groupId)
            }
            urlsArray.put(urlObject)
        }
        rootObject.put("urls", urlsArray)

        return rootObject
    }

    private fun restoreFromBackup(jsonString: String) {
        val rootObject = JSONObject(jsonString)

        val groupsArray = rootObject.getJSONArray("groups")
        val groups = mutableListOf<UrlGroup>()
        for (i in 0 until groupsArray.length()) {
            val groupObject = groupsArray.getJSONObject(i)
            groups.add(
                UrlGroup(
                    id = groupObject.getLong("id"),
                    name = groupObject.getString("name"),
                    isExpanded = groupObject.getBoolean("isExpanded"),
                    orderIndex = groupObject.getInt("orderIndex")
                )
            )
        }

        val urlsArray = rootObject.getJSONArray("urls")
        val urls = mutableListOf<SearchUrl>()
        for (i in 0 until urlsArray.length()) {
            val urlObject = urlsArray.getJSONObject(i)
            urls.add(
                SearchUrl(
                    id = urlObject.getInt("id"),
                    name = urlObject.getString("name"),
                    urlPattern = urlObject.getString("urlPattern"),
                    isEnabled = urlObject.getBoolean("isEnabled"),
                    orderIndex = urlObject.getInt("orderIndex"),
                    groupId = urlObject.getLong("groupId")
                )
            )
        }

        searchViewModel.restoreFromBackup(groups, urls)
    }

    private fun readTextFromStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line)
        }
        return stringBuilder.toString()
    }

    private fun setupRecyclerView() {
        groupedAdapter = GroupedSearchUrlAdapter(
            onItemClicked = { item ->
                if (item is SearchUrl) {
                    val searchQuery = binding.editTextSearch.text.toString().trim()
                    // 无论是否输入关键词，都尝试打开链接
                    UrlLauncher.launchSearchUrls(this, searchQuery, listOf(item))
                }
            },
            onDeleteItemClicked = { item ->
                if (item is SearchUrl) showDeleteConfirmationDialog(item)
                else if (item is UrlGroup) showDeleteGroupDialog(item)
            },
            onUrlCheckChanged = { searchUrl, isChecked ->
                searchViewModel.updateUrl(searchUrl.copy(isEnabled = isChecked))
            },
            onGroupCheckChanged = { group, isChecked ->
                searchViewModel.updateGroupSelected(group, isChecked)
            },
            onGroupExpandCollapse = { group, isExpanded ->
                searchViewModel.updateGroupExpanded(group, isExpanded)
            },
            onItemMoveRequested = { fromPosition, toPosition ->
                handleItemMove(fromPosition, toPosition)
            },
            getUrlsForGroup = { groupId ->
                // 使用 ViewModel 获取分组的所有 URL，无论是否展开
                searchViewModel.getUrlsByGroupId(groupId)
            }
        )
        binding.recyclerViewUrls.adapter = groupedAdapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
             0
        ) {
            private var dragFrom: Int = -1
            private var dragTo: Int = -1
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                
                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    // 追踪开始的拖动位置
                    if (dragFrom == -1) {
                        dragFrom = fromPosition
                    }
                    // 更新当前拖动位置
                    dragTo = toPosition
                    
                    // 移动视图显示
                    groupedAdapter.notifyItemMoved(fromPosition, toPosition)
                    return true
                }
                return false
            }

            // 拖放完成时处理实际数据更新
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                
                // 如果我们有有效的拖动操作
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    // 执行实际的数据移动
                    handleItemMove(dragFrom, dragTo)
                }
                
                // 重置拖放状态
                dragFrom = -1
                dragTo = -1
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = current.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false

                val itemFrom = groupedAdapter.getItemAt(fromPosition)
                val itemTo = groupedAdapter.getItemAt(toPosition)

                return when {
                    // 组可以移动到组
                    itemFrom is UrlGroup && itemTo is UrlGroup -> true
                    // 链接可以在同一组内移动
                    itemFrom is SearchUrl && itemTo is SearchUrl -> {
                        itemFrom.groupId == itemTo.groupId
                    }
                    // 链接可以移动到组
                    itemFrom is SearchUrl && itemTo is UrlGroup -> true
                    // 其他情况不允许移动
                    else -> false
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewUrls)
    }

    private fun handleItemMove(fromPosition: Int, toPosition: Int) {
        val itemFrom = groupedAdapter.getItemAt(fromPosition)
        val itemTo = groupedAdapter.getItemAt(toPosition)

        when {
            itemFrom is UrlGroup && itemTo is UrlGroup -> {
                val mutableGroups = searchViewModel.allGroups.value.toMutableList()
                val movedGroup = mutableGroups.removeAt(groupedAdapter.getGroupOriginalIndex(itemFrom))
                mutableGroups.add(groupedAdapter.getGroupOriginalIndex(itemTo), movedGroup)
                searchViewModel.updateGroupsOrder(mutableGroups.mapIndexed { index, group -> group.copy(orderIndex = index) })
            }
            itemFrom is SearchUrl && itemTo is SearchUrl -> {
                // 判断是否在同一分组内
                if (itemFrom.groupId == itemTo.groupId) {
                    // 获取同一分组内的所有URL
                    val groupUrls = searchViewModel.getUrlsByGroupId(itemFrom.groupId)
                    val itemFromIndex = groupUrls.indexOfFirst { it.id == itemFrom.id }
                    val itemToIndex = groupUrls.indexOfFirst { it.id == itemTo.id }

                    if (itemFromIndex != -1 && itemToIndex != -1) {
                        // 重新排序
                        val reorderedUrls = groupUrls.toMutableList()
                        val movedItem = reorderedUrls.removeAt(itemFromIndex)
                        reorderedUrls.add(itemToIndex, movedItem)

                        // 更新顺序索引
                        val updatedUrls = reorderedUrls.mapIndexed { index, url ->
                            url.copy(orderIndex = index)
                        }
                        searchViewModel.updateUrls(updatedUrls)
                    }
                } else {
                    // 跨分组移动
                    searchViewModel.moveUrlToGroup(itemFrom, itemTo.groupId, itemTo.orderIndex)
                }
            }
            itemFrom is SearchUrl && itemTo is UrlGroup -> {
                // 移动到分组顶部
                searchViewModel.moveUrlToGroup(itemFrom, itemTo.id, 0)
            }
        }
    }

    private fun showAddUrlDialog() {
        val dialogBinding = DialogAddUrlBinding.inflate(LayoutInflater.from(this))
        val currentGroups = searchViewModel.allGroups.value

        if (currentGroups.isEmpty()) {
            Toast.makeText(this, "请先创建一个分组", Toast.LENGTH_SHORT).show()
            return
        }

        val groupNames = currentGroups.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerGroup.adapter = adapter
        dialogBinding.spinnerGroup.setSelection(0)

        val dialog = AlertDialog.Builder(this)
            .setTitle("添加搜索链接")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim()
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val selectedGroupPosition = dialogBinding.spinnerGroup.selectedItemPosition
            val selectedGroup = currentGroups[selectedGroupPosition]

            if (name.isNotEmpty() && pattern.isNotEmpty()) {
                val searchUrl = SearchUrl(
                    name = name,
                    urlPattern = pattern,
                    groupId = selectedGroup.id
                )
                searchViewModel.insert(searchUrl)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "名称和链接不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddGroupDialog() {
        val dialogBinding = DialogAddGroupBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setTitle("添加分组")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val groupName = dialogBinding.editTextGroupName.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    searchViewModel.addGroup(groupName)
                } else {
                    Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(searchUrl: SearchUrl) {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除 ${searchUrl.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.delete(searchUrl)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteGroupDialog(group: UrlGroup) {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除分组 ${group.name} 及其包含的所有链接吗？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.deleteGroup(group)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupSearchFunction() {
        binding.buttonSearch.setOnClickListener {
            performSearch()
        }

        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val searchQuery = binding.editTextSearch.text.toString().trim()
        if (searchQuery.isNotEmpty()) {
            val selectedUrls = searchViewModel.allUrls.value?.filter { it.isEnabled } ?: listOf()
            if (selectedUrls.isNotEmpty()){
                UrlLauncher.launchSearchUrls(this, searchQuery, selectedUrls)
            } else {
                Toast.makeText(this, "没有选中的搜索链接", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAddUrlButton() {
        binding.buttonAddUrl.setOnClickListener {
            showAddUrlDialog()
        }
    }

    private fun setupAddGroupButton() {
        binding.buttonAddGroup.setOnClickListener {
            showAddGroupDialog()
        }
    }
}
