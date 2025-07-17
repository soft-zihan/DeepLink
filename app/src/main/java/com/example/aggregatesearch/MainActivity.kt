package com.example.aggregatesearch

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.adapters.PinnedSearchAdapter
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import com.example.aggregatesearch.databinding.ActivityMainBinding
import com.example.aggregatesearch.databinding.DialogAddGroupBinding
import com.example.aggregatesearch.databinding.DialogAddUrlBinding
import com.example.aggregatesearch.databinding.DialogEditGroupBinding
import com.example.aggregatesearch.databinding.DialogEditUrlBinding
import com.example.aggregatesearch.utils.ColorPickerDialog
import com.example.aggregatesearch.utils.IconLoader
import com.example.aggregatesearch.utils.PinnedSearchManager
import com.example.aggregatesearch.utils.SearchHistoryManager
import com.example.aggregatesearch.utils.UiUtils
import com.example.aggregatesearch.utils.AppPreferences
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var pinnedAdapter: PinnedSearchAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // 搜索历史管理
    private lateinit var searchHistoryManager: SearchHistoryManager

    // 常搜（固定）管理
    private lateinit var pinnedSearchManager: PinnedSearchManager

    // 应用设置管理
    private lateinit var appPreferences: AppPreferences

    // 全选按钮状态
    private var isAllSelected: Boolean = false

    // 选择应用结果回调�������关
    private var currentPackageNameEditText: EditText? = null
    private var currentSelectedAppNameTextView: TextView? = null
    private var onAppSelectedGlobal: ((String, String) -> Unit)? = null
    private lateinit var appSelectionResultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    private var isPinnedEditMode = false

    // 编辑模式标志
    private var isEditMode = false
    private var menuEditMode: MenuItem? = null
    private var menuPlus: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 必须在其他UI初始化之前设置好 Toolbar
            setSupportActionBar(binding.toolbar)

            // 应用顶栏颜色
            UiUtils.applyToolbarColor(this)

            // 应用壁��
            UiUtils.applyWallpaper(binding.root, this)

            // 初始化搜索历史管理
            searchHistoryManager = SearchHistoryManager(this)

            // 初始化常搜管理
            pinnedSearchManager = PinnedSearchManager(this)

            // 初始化应用设置管理
            appPreferences = AppPreferences(this)

            // 注册应用选择结果回调
            appSelectionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    val packageName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_PACKAGE_NAME) ?: ""
                    val appName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_APP_NAME) ?: ""
                    onAppSelectedGlobal?.invoke(packageName, appName)
                }
            }

            setupRecyclerView()
            setupSearchFunction()
            setupPinnedSearches()

            lifecycleScope.launch {
                searchViewModel.groupedItems.collectLatest {
                    groupedAdapter.submitList(it)
                }
            }

            setupSelectAllButton()

            refreshSearchHistorySuggestions()
            refreshPinnedSearches()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "发生错误，请查看日志: ${e.message}", Toast.LENGTH_LONG).show()
            // 出现严重错误时，可以考虑关闭应用
            // finish()
        }
    }

    override fun onResume() {
        super.onResume()
        UiUtils.applyToolbarColor(this)
        // 同步搜索按钮颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        binding.buttonSearch.backgroundTintList = ColorStateList.valueOf(colorStr.toColorInt())

        UiUtils.applyWallpaper(binding.root, this)

        refreshSearchHistorySuggestions()
        refreshPinnedSearches()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menuEditMode = menu.findItem(R.id.menu_edit_mode)
        menuPlus = menu.findItem(R.id.menu_plus)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit_mode -> {
                // 切换编辑模式
                isEditMode = !isEditMode
                updateEditModeUI()
                true
            }
            R.id.menu_pin -> {
                val query = binding.editTextSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    pinnedSearchManager.addPin(query)
                    refreshPinnedSearches()
                    Toast.makeText(this, "已固定", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入内容后再固定", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.menu_plus -> {
                // 二选一弹窗
                AlertDialog.Builder(this)
                    .setTitle("请选择要添的内容")
                    .setItems(arrayOf("创建������", "创建链接")) { _, which ->
                        if (which == 0) {
                            showAddGroupDialog()
                        } else {
                            showAddUrlDialog()
                        }
                    }
                    .show()
                true
            }
            R.id.menu_settings -> {
                // 打开设置界面
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_help -> {
                // 显示使用说明
                showHelpDialog()
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
                Toast.makeText(this, "恢复成功", Toast.LENGTH_SHORT).show()
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
        searchViewModel.allUrls.value.forEach { url ->
            val urlObject = JSONObject().apply {
                put("id", url.id)
                put("name", url.name)
                put("urlPattern", url.urlPattern)
                put("isEnabled", url.isEnabled)
                put("orderIndex", url.orderIndex)
                put("groupId", url.groupId)
                // 添加包名到备份数据中
                put("packageName", url.packageName)
                // 新增：备份文字图标相关设置
                put("useTextIcon", url.useTextIcon)
                put("iconText", url.iconText)
                put("iconBackgroundColor", url.iconBackgroundColor)
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
                    id = urlObject.getLong("id"),
                    name = urlObject.getString("name"),
                    urlPattern = urlObject.getString("urlPattern"),
                    isEnabled = urlObject.getBoolean("isEnabled"),
                    orderIndex = urlObject.getInt("orderIndex"),
                    groupId = urlObject.getLong("groupId"),
                    // 尝试从备份数据中恢复包名，如果不存在则使用空字符串
                    packageName = urlObject.optString("packageName", ""),
                    // 新增：恢复文字图标相关设置，提���默认值以兼容旧备份
                    useTextIcon = urlObject.optBoolean("useTextIcon", false),
                    iconText = urlObject.optString("iconText", ""),
                    iconBackgroundColor = urlObject.optInt("iconBackgroundColor", 0xFF2196F3.toInt())
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
                } else if (item is UrlGroup) {
                    // 当点击分组时，获取该分组内所有已勾选的链接并打开
                    val searchQuery = binding.editTextSearch.text.toString().trim()
                    val enabledUrlsInGroup = searchViewModel.getUrlsByGroupId(item.id).filter { it.isEnabled }
                    if (enabledUrlsInGroup.isNotEmpty()) {
                        UrlLauncher.launchSearchUrls(this, searchQuery, enabledUrlsInGroup)
                    } else {
                        Toast.makeText(this, "该分组内没有启用的链接", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteItemClicked = { item ->
                if (item is SearchUrl) showDeleteConfirmationDialog(item, this)
                else if (item is UrlGroup) showDeleteGroupDialog(item, this)
            },
            onEditItemClicked = { item ->
                // 新增编辑功能
                if (item is SearchUrl) showEditUrlDialog(item)
                else if (item is UrlGroup) showEditGroupDialog(item)
            },
            onAddUrlClicked = { group ->
                showAddUrlDialog(group)
            },
            onUrlCheckChanged = { searchUrl, isChecked ->
                // 使用新方法来处理链接状态变化
                searchViewModel.onUrlCheckChanged(searchUrl, isChecked)
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

        // 监听分组状态变化事件
        searchViewModel.groupCheckEvents.observe(this) { (group, isChecked) ->
            // 通知适配器更新分组状态
            groupedAdapter.updateGroupCheckedState(group, isChecked)
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var dragFrom: Int = -1
            private var dragTo: Int = -1
            private var draggingGroupId: Long? = null // 跟踪当前正在拖动的分组ID

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    val itemFrom = groupedAdapter.getItemAt(fromPosition)
                    val itemTo = groupedAdapter.getItemAt(toPosition)

                    // 追踪开始的拖动位置
                    if (dragFrom == -1) {
                        dragFrom = fromPosition
                        // 如果是分组，记录分组ID以���整体拖��
                        if (itemFrom is UrlGroup) {
                            draggingGroupId = itemFrom.id
                        } else if (itemFrom is SearchUrl) {
                            draggingGroupId = itemFrom.groupId
                        }
                    }

                    // 更新当前拖动位置
                    dragTo = toPosition

                    // 移动视图显示
                    groupedAdapter.notifyItemMoved(fromPosition, toPosition)

                    // 如果是分组，同时移动分组下的所有图标容器
                    if (draggingGroupId != null && itemFrom is UrlGroup && itemTo is UrlGroup) {
                        // 这里需要确保分��和其下的图���容器���起移动，保持整体性
                        // 因为每个分组后面紧跟一个图标容器，所以同时移动下一个位置
                        if (fromPosition + 1 < groupedAdapter.itemCount &&
                            toPosition + 1 < groupedAdapter.itemCount) {
                            groupedAdapter.notifyItemMoved(fromPosition + 1, toPosition + 1)
                        }
                    }

                    return true
                }
                return false
            }

            // 拖放完成时处理实际数据更新
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                // 如果我们有有效的拖动操作
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    // 执行实际的数据移动，使用当前列表中的实际位置
                    val adapter = recyclerView.adapter as GroupedSearchUrlAdapter
                    val fromItem = adapter.getItemAt(dragFrom)
                    val toItem = adapter.getItemAt(dragTo)

                    // 根据实际的项目类型执行相应的数据更新
                    if (fromItem is UrlGroup && toItem is UrlGroup) {
                        // 使用ViewModel中的数据而非adapter中的数据
                        val groups = searchViewModel.allGroups.value?.toMutableList() ?: return
                        val fromGroup = groups.find { it.id == (fromItem as UrlGroup).id }
                        val toGroup = groups.find { it.id == (toItem as UrlGroup).id }
                        if (fromGroup != null && toGroup != null) {
                            // 获取正确的顺序索引
                            val fromIndex = groups.indexOf(fromGroup)
                            val toIndex = groups.indexOf(toGroup)
                            if (fromIndex != -1 && toIndex != -1) {
                                // 移动组并重新计算索引
                                val movedGroup = groups.removeAt(fromIndex)
                                groups.add(if (toIndex > fromIndex) toIndex - 1 else toIndex, movedGroup)

                                // 更新所有组的顺序索引
                                val updatedGroups = groups.mapIndexed { index, group ->
                                    group.copy(orderIndex = index)
                                }

                                // 提交更新
                                searchViewModel.updateGroupsOrder(updatedGroups)
                            }
                        }
                    } else if (fromItem is SearchUrl && toItem is SearchUrl && fromItem.groupId == toItem.groupId) {
                        // 处���同组内链接的排序
                        val urls = searchViewModel.getUrlsByGroupId(fromItem.groupId)
                        val fromUrl = urls.find { it.id == fromItem.id }
                        val toUrl = urls.find { it.id == toItem.id }
                        if (fromUrl != null && toUrl != null) {
                            val fromIndex = urls.indexOf(fromUrl)
                            val toIndex = urls.indexOf(toUrl)
                            if (fromIndex != -1 && toIndex != -1) {
                                val reorderedUrls = urls.toMutableList()
                                val movedUrl = reorderedUrls.removeAt(fromIndex)
                                reorderedUrls.add(if (toIndex > fromIndex) toIndex - 1 else toIndex, movedUrl)

                                // 更新顺序索引
                                val updatedUrls = reorderedUrls.mapIndexed { index, url ->
                                    url.copy(orderIndex = index)
                                }

                                // 提交更新
                                searchViewModel.updateUrls(updatedUrls)
                            }
                        }
                    }
                }

                // 重置拖放状态
                dragFrom = -1
                dragTo = -1
                draggingGroupId = null
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return 0

                val item = groupedAdapter.getItemAt(position)

                // 不限制编辑模式，�����何模式下都可以使用拖动排序功能
                if (item is UrlGroup) {
                    // 分组可以上下拖动
                    return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                } else if (item is SearchUrl) {
                    // 链接可以上下移动
                    val urls = groupedAdapter.getItems().filterIsInstance<SearchUrl>().filter { it.groupId == item.groupId }
                    // 只有当分组中有多个链接时才允许拖动
                    return if (urls.size > 1) makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                    else 0
                }

                return 0 // 其他项目不能拖动
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
                    // 组只能移动到组
                    itemFrom is UrlGroup && itemTo is UrlGroup -> true
                    // 链接只能在同一组内移动
                    itemFrom is SearchUrl && itemTo is SearchUrl -> {
                        itemFrom.groupId == itemTo.groupId
                    }
                    // 链接不能移动到组，组不能移动到链接
                    else -> false
                }
            }

            // 禁用��按拖动，改为通过拖动手柄���特定区域触发
            override fun isLongPressDragEnabled(): Boolean = false

            // 禁用滑动删除
            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewUrls)

        // 将ItemTouchHelper设置为标签，以便在适配器中被找到
        binding.recyclerViewUrls.setTag(R.id.item_touch_helper, itemTouchHelper)
    }

    private fun setupPinnedSearches() {
        binding.recyclerViewPinned.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        pinnedAdapter = PinnedSearchAdapter(
            pinnedWords = pinnedSearchManager.getPinned().toMutableList(),
            onWordClick = { word ->
                binding.editTextSearch.setText(word)
                performSearch()
            },
            onDeleteClick = { word ->
                pinnedSearchManager.removePin(word)
                pinnedAdapter.removeWord(word)
                Toast.makeText(this, "已删除: $word", Toast.LENGTH_SHORT).show()
                refreshPinnedSearches() // 刷新以更新可见性
            }
        )
        binding.recyclerViewPinned.adapter = pinnedAdapter

        refreshPinnedSearches()
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
                // 移��到分组顶部
                searchViewModel.moveUrlToGroup(itemFrom, itemTo.id, 0)
            }
        }
    }

    private fun showAddUrlDialog() {
        showAddUrlDialog(null)
    }

    private fun showAddUrlDialog(preSelectedGroup: UrlGroup?) {
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

        if (preSelectedGroup != null) {
            val idx = currentGroups.indexOfFirst { g -> g.id == preSelectedGroup.id }
            if (idx >= 0) dialogBinding.spinnerGroup.setSelection(idx)
        } else {
            dialogBinding.spinnerGroup.setSelection(0)
        }

        // 设置选择应用按钮点击事件
        var selectedPackageName = ""

        currentPackageNameEditText = dialogBinding.editTextPackageName
        currentSelectedAppNameTextView = dialogBinding.textViewSelectedAppName
        onAppSelectedGlobal = { packageName: String, appName: String ->
            selectedPackageName = packageName
            currentPackageNameEditText?.setText(packageName)
            if (packageName.isNotEmpty()) {
                currentSelectedAppNameTextView?.text = getString(R.string.selected_app_format, appName)
                currentSelectedAppNameTextView?.visibility = View.VISIBLE
            } else {
                currentSelectedAppNameTextView?.visibility = View.GONE
            }
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            appSelectionResultLauncher.launch(Intent(this, com.example.aggregatesearch.activities.AppSelectionActivity::class.java))
        }

        // 添加取消绑定按钮的功能
        dialogBinding.btnCancelAppBinding.setOnClickListener {
            // 清空应用绑定信息
            selectedPackageName = ""
            dialogBinding.editTextPackageName.setText("")
            dialogBinding.textViewSelectedAppName.visibility = View.GONE
            Toast.makeText(this, "已取消应用绑定", Toast.LENGTH_SHORT).show()
        }

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

            if (name.isNotEmpty()) {
                val searchUrl = SearchUrl(
                    name = name,
                    urlPattern = pattern,
                    groupId = selectedGroup.id,
                    packageName = selectedPackageName
                )
                searchViewModel.insert(searchUrl)
                dialog.dismiss()
                // 如果在编辑模式下添加了链接，则退出编辑模式
                if (isEditMode) {
                    isEditMode = false
                    updateEditModeUI()
                }
            } else {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
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

    private fun showDeleteConfirmationDialog(searchUrl: SearchUrl, context: Context, editDialog: Dialog? = null) {
        AlertDialog.Builder(context)
            .setTitle("删除确认")
            .setMessage("确定要删除 ${searchUrl.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.delete(searchUrl)
                // 不再需要手动调用 notifyDataSetChanged()��因为 LiveData/Flow 会自动更新UI
                editDialog?.dismiss()
                // 强制刷新Activity以确保UI更新
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteGroupDialog(group: UrlGroup, context: Context) {
        AlertDialog.Builder(context)
            .setTitle("删除确认")
            .setMessage("确定要删除分组 ${group.name} 其包含的所有链接？")
            .setPositiveButton("删除") { _, _ ->
                searchViewModel.deleteGroup(group)
                // 强制刷新Activity以确保UI更新
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditUrlDialog(searchUrl: SearchUrl) {
        val dialogBinding = DialogEditUrlBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑搜索链接")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        // 获取并应用主题颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val color = colorStr.toColorInt()
        val colorStateList = ColorStateList.valueOf(color)

        dialogBinding.btnTestUrl.backgroundTintList = colorStateList
        dialogBinding.buttonDeleteUrlDialog.backgroundTintList = colorStateList
        dialogBinding.btnSelectApp.setTextColor(color)
        dialogBinding.btnCancelAppBinding.setTextColor(color)


        // 预填充现有值
        dialogBinding.editTextUrlName.setText(searchUrl.name)
        dialogBinding.editTextUrlPattern.setText(searchUrl.urlPattern)

        // 预填充文字图标设置
        dialogBinding.radioButtonTextIcon.isChecked = searchUrl.useTextIcon
        dialogBinding.radioButtonAutoIcon.isChecked = !searchUrl.useTextIcon
        dialogBinding.layoutTextIcon.visibility = if (searchUrl.useTextIcon) View.VISIBLE else View.GONE

        // 预填充图标文本，如果为空则使用链接名称
        val iconText = if (searchUrl.iconText.isNotBlank()) searchUrl.iconText else searchUrl.name
        dialogBinding.editTextIconText.setText(iconText)

        // 设置图标背景颜色
        dialogBinding.viewIconBackgroundColor.setBackgroundColor(searchUrl.iconBackgroundColor)

        // 设置图标预览
        updateIconPreview(dialogBinding, searchUrl)

        // 设置图标类型切换监听
        dialogBinding.radioGroupIconType.setOnCheckedChangeListener { _, checkedId ->
            val useTextIcon = checkedId == R.id.radioButtonTextIcon
            dialogBinding.layoutTextIcon.visibility = if (useTextIcon) View.VISIBLE else View.GONE
            updateIconPreview(dialogBinding, searchUrl.copy(useTextIcon = useTextIcon))
        }

        // 为刷新图标按钮添加点击事件
        dialogBinding.btnRefreshIcon.setOnClickListener {
            if (dialogBinding.radioButtonAutoIcon.isChecked) {
                // 清除缓存的图标（如果有缓存机制）
                Toast.makeText(this, "正在刷新图标...", Toast.LENGTH_SHORT).show()
                val tempUrl = searchUrl.copy()
                tempUrl.forceRefresh = true
                updateIconPreview(dialogBinding, tempUrl)
            }
        }

        // 文字图标文本变更监听
        dialogBinding.editTextIconText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (dialogBinding.radioButtonTextIcon.isChecked) {
                    val currentBgColor = (dialogBinding.viewIconBackgroundColor.background as? ColorDrawable)?.color ?: searchUrl.iconBackgroundColor
                    updateIconPreview(dialogBinding, searchUrl.copy(iconText = s.toString(), iconBackgroundColor = currentBgColor))
                }
            }
        })

        // 颜色选择功能
        dialogBinding.viewIconBackgroundColor.setOnClickListener {
            try {
                com.example.aggregatesearch.utils.ColorPickerDialog.Builder(this)
                    .setTitle("选择���标颜色")
                    .setColorShape(true)
                    .setDefaultColor(searchUrl.iconBackgroundColor)
                    .setColorListener { color, _ ->
                        dialogBinding.viewIconBackgroundColor.setBackgroundColor(color)
                        if (dialogBinding.radioButtonTextIcon.isChecked) {
                            val currentIconText = dialogBinding.editTextIconText.text.toString()
                            updateIconPreview(dialogBinding, searchUrl.copy(iconText = currentIconText, iconBackgroundColor = color))
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "打开颜色选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Error showing color picker", e)
            }
        }

        // 预填包名
        var selectedPackageName = searchUrl.packageName
        dialogBinding.editTextPackageName.setText(selectedPackageName)

        // 显示已选择的应用名称(如果有)
        if (selectedPackageName.isNotEmpty()) {
            try {
                val packageInfo = packageManager.getApplicationInfo(selectedPackageName, 0)
                val appName = packageManager.getApplicationLabel(packageInfo).toString()
                dialogBinding.textViewSelectedAppName.text = getString(R.string.selected_app_format, appName)
                dialogBinding.textViewSelectedAppName.visibility = View.VISIBLE
                dialogBinding.btnCancelAppBinding.visibility = View.VISIBLE
            } catch (e: Exception) {
                dialogBinding.textViewSelectedAppName.visibility = View.GONE
                dialogBinding.btnCancelAppBinding.visibility = View.GONE
            }
        } else {
            dialogBinding.textViewSelectedAppName.visibility = View.GONE
            dialogBinding.btnCancelAppBinding.visibility = View.GONE
        }

        // 设置选择应用按钮点击事件
        currentPackageNameEditText = dialogBinding.editTextPackageName
        currentSelectedAppNameTextView = dialogBinding.textViewSelectedAppName
        onAppSelectedGlobal = { packageName, appName ->
            selectedPackageName = packageName
            currentPackageNameEditText?.setText(packageName)
            if (packageName.isNotEmpty()) {
                currentSelectedAppNameTextView?.text = getString(R.string.selected_app_format, appName)
                currentSelectedAppNameTextView?.visibility = View.VISIBLE
                dialogBinding.btnCancelAppBinding.visibility = View.VISIBLE
            } else {
                currentSelectedAppNameTextView?.visibility = View.GONE
                dialogBinding.btnCancelAppBinding.visibility = View.GONE
            }
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            appSelectionResultLauncher.launch(Intent(this, com.example.aggregatesearch.activities.AppSelectionActivity::class.java))
        }

        // 添加取消绑定按钮的功能
        dialogBinding.btnCancelAppBinding.setOnClickListener {
            // 清空应用绑定信息
            selectedPackageName = ""
            dialogBinding.editTextPackageName.setText("")
            dialogBinding.textViewSelectedAppName.text = ""
            dialogBinding.textViewSelectedAppName.visibility = View.GONE
            dialogBinding.btnCancelAppBinding.visibility = View.GONE
            Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
        }

        // 测试链接按钮
        dialogBinding.btnTestUrl.setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim().ifEmpty { "测试" }
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val tempUrl = searchUrl.copy(name = name, urlPattern = pattern, packageName = selectedPackageName)
            UrlLauncher.launchSearchUrls(this, binding.editTextSearch.text.toString().trim(), listOf(tempUrl))
        }

        // 删除按钮
        dialogBinding.buttonDeleteUrlDialog.setOnClickListener {
            showDeleteConfirmationDialog(searchUrl, this, dialog)
        }

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim()
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val useTextIcon = dialogBinding.radioButtonTextIcon.isChecked
            var iconText = dialogBinding.editTextIconText.text.toString().trim()

            // 确保当使用文字图标时，始终有有效的文字内容
            if (useTextIcon && iconText.isEmpty()) {
                iconText = name.take(1) // 使用名称的第一个字符作为默认值
            }

            val bgColor = (dialogBinding.viewIconBackgroundColor.background as? ColorDrawable)?.color ?: searchUrl.iconBackgroundColor

            if (name.isNotEmpty()) {
                val updatedUrl = searchUrl.copy(
                    name = name,
                    urlPattern = pattern,
                    packageName = selectedPackageName,
                    useTextIcon = useTextIcon,
                    iconText = iconText,
                    iconBackgroundColor = bgColor
                )
                searchViewModel.updateUrl(updatedUrl)
                dialog.dismiss()
                // 强制刷新Activity以确保UI更新
                recreate()
            } else {
                Toast.makeText(this, "名称��能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditGroupDialog(group: UrlGroup) {
        val dialogBinding = DialogEditGroupBinding.inflate(LayoutInflater.from(this))
        var selectedColor: String? = group.color

        // 获取并应用主题颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val color = colorStr.toColorInt()
        val colorStateList = ColorStateList.valueOf(color)
        dialogBinding.buttonSelectGroupColor.backgroundTintList = colorStateList
        dialogBinding.buttonDeleteGroupDialog.backgroundTintList = colorStateList
        dialogBinding.buttonSelectGroupColor.setTextColor(Color.WHITE)
        dialogBinding.buttonDeleteGroupDialog.setTextColor(Color.WHITE)

        dialogBinding.editTextGroupName.setText(group.name)
        selectedColor?.let {
            dialogBinding.viewSelectedGroupColor.setBackgroundColor(it.toColorInt())
        }

        dialogBinding.buttonSelectGroupColor.setOnClickListener {
            try {
                com.example.aggregatesearch.utils.ColorPickerDialog.Builder(this)
                    .setTitle("选择分组颜色")
                    .setColorShape(false)
                    .setDefaultColor(selectedColor ?: "#FFFFFF")
                    .setColorListener { color, hexColor ->
                        selectedColor = hexColor
                        dialogBinding.viewSelectedGroupColor.setBackgroundColor(color)
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "打开颜色选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Error showing color picker", e)
            }
        }

        dialogBinding.buttonDeleteGroupDialog.setOnClickListener {
            showDeleteGroupDialog(group, this)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑分组")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextGroupName.text.toString().trim()

            if (name.isNotEmpty()) {
                val updatedGroup = group.copy(name = name, color = selectedColor)
                searchViewModel.updateGroup(updatedGroup)
                dialog.dismiss()
                // 强制刷新Activity以确保UI更新
                recreate()
            } else {
                Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // 为编辑对话框更新图标预览
    private fun updateIconPreview(dialogBinding: DialogEditUrlBinding, searchUrl: SearchUrl) {
        // 不再在这里设置RadioButton状态，避免与用户操作冲突
        // 只根据传入的searchUrl属性更新预览

        // 根据当前模式设置图标预览
        if (searchUrl.useTextIcon) {
            // 文字图标模式：使用自定义文字图标
            // 优先使用对话框中当前输入的文本，如果为空则使用searchUrl对象的文本
            val text = dialogBinding.editTextIconText.text.toString().takeIf { it.isNotBlank() }
                ?: searchUrl.iconText.takeIf { it.isNotBlank() }
                ?: searchUrl.name

            // 优先使用当前对话框中选择的背景颜色
            val bgColor = (dialogBinding.viewIconBackgroundColor.background as? android.graphics.drawable.ColorDrawable)?.color
                ?: searchUrl.iconBackgroundColor

            val drawable = com.example.aggregatesearch.utils.IconLoader.createTextIcon(
                dialogBinding.root.context,
                text,
                bgColor
            )
            dialogBinding.imageViewIconPreview.setImageDrawable(drawable)

            // 隐藏刷新图标按钮，因为文字图标不需要刷新
            dialogBinding.btnRefreshIcon.visibility = View.GONE
        } else {
            // 自动图标模式：尝试加载网络图标
            com.example.aggregatesearch.utils.IconLoader.loadIcon(
                dialogBinding.root.context,
                searchUrl,
                dialogBinding.imageViewIconPreview
            )

            // 显示刷新图标按钮
            dialogBinding.btnRefreshIcon.visibility = View.VISIBLE
        }
    }

    // 更新编辑模式UI状态
    private fun updateEditModeUI() {
        // 更新编辑按钮图标
        menuEditMode?.icon = resources.getDrawable(
            if (isEditMode) android.R.drawable.ic_menu_close_clear_cancel
            else android.R.drawable.ic_menu_edit,
            theme
        )

        // 更新适配器中的编辑模式状态
        groupedAdapter.setEditMode(isEditMode)

        // 同时更新固定搜索词的编辑模式状态，使其与全局编辑模式一致
        pinnedAdapter.setEditMode(isEditMode)

        // 根据编辑模式控制添加按钮的可见性
        menuPlus?.isVisible = !isEditMode // 编辑模式下隐藏添加按钮，由组内的添加按钮代替

        // 显示提示信息
        if (isEditMode) {
            Toast.makeText(this, "已进入编辑模式", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已退出编辑模式", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchFunction() {
        // 设置搜索按钮点击事件
        binding.buttonSearch.setOnClickListener {
            performSearch()
        }

        // 设置编辑框回车键��索
        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                return@setOnEditorActionListener true
            }
            false
        }

        // 点击搜索框时，如果已经有内容，则显示建议
        binding.editTextSearch.setOnTouchListener { v, _ ->
            (v as? AutoCompleteTextView)?.showDropDown()
            false
        }

        // 当搜索框获得焦点时显示历史记录
        binding.editTextSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                (binding.editTextSearch as? AutoCompleteTextView)?.showDropDown()
            }
        }
    }

    private fun performSearch() {
        val query = binding.editTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            // 添加到搜索历史
            searchHistoryManager.addQuery(query)

            // 获取所有勾选的��接
            val enabledUrls = searchViewModel.getEnabledUrls().filterIsInstance<SearchUrl>()

            if (enabledUrls.isNotEmpty()) {
                // 启动搜索
                UrlLauncher.launchSearchUrls(this, query, enabledUrls)
            } else {
                Toast.makeText(this, "请先选择至少一个搜索引擎", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSelectAllButton() {
        binding.buttonSelectAll.setOnClickListener {
            // 切换全选状态
            isAllSelected = !isAllSelected

            // 更新所有项的选中状态
            searchViewModel.setAllSelected(isAllSelected)

            // 等待数据库更新完成后再刷新UI
            lifecycleScope.launch {
                // 给数据库操作一些时间完成
                kotlinx.coroutines.delay(100)

                // 强制刷新所有图标状态，确保UI与数据库状态同步
                groupedAdapter.forceRefreshAllIconStates()
            }

            // 更新按钮文字
            if (it is Button) {
                it.text = getString(if (isAllSelected) R.string.deselect_all else R.string.select_all)
            }

            // 显示提示
            val message = if (isAllSelected) "已全选" else "已取消全选"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshSearchHistorySuggestions() {
        // 获取搜索历史
        val histories = searchHistoryManager.getHistory()

        // 更新搜索框的自动完成建议
        if (binding.editTextSearch is AutoCompleteTextView) {
            val autoCompleteTextView = binding.editTextSearch as AutoCompleteTextView
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                histories.toTypedArray()
            )
            autoCompleteTextView.setAdapter(adapter)
        }
    }

    private fun refreshPinnedSearches() {
        val pinnedWords = pinnedSearchManager.getPinned()
        if (pinnedWords.isNotEmpty()) {
            binding.recyclerViewPinned.visibility = View.VISIBLE
            pinnedAdapter.setWords(pinnedWords)
        } else {
            binding.recyclerViewPinned.visibility = View.GONE
        }
    }




    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("使用说明")
            .setView(dialogView)
            .setPositiveButton("知道了", null)
            .create()

        dialog.show()
    }
}
