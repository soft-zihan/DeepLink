package com.example.aggregatesearch

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
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
import com.example.aggregatesearch.utils.AppPackageManager
import com.example.aggregatesearch.utils.SearchHistoryManager
import com.example.aggregatesearch.utils.UiUtils
import com.example.aggregatesearch.utils.PinnedSearchManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import android.content.res.ColorStateList
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((application as SearchApplication).repository)
    }
    private lateinit var groupedAdapter: GroupedSearchUrlAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // 搜索历史管理
    private lateinit var searchHistoryManager: SearchHistoryManager

    // 常搜（固定）管理
    private lateinit var pinnedSearchManager: PinnedSearchManager

    // 全选按钮状态
    private var isAllSelected: Boolean = false

    // 选择应用结果回调相关
    private var currentPackageNameEditText: android.widget.EditText? = null
    private var currentSelectedAppNameTextView: android.widget.TextView? = null
    private var onAppSelectedGlobal: ((String, String) -> Unit)? = null
    private lateinit var appSelectionResultLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    private val createBackupFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportBackup(it) }
    }

    private val openRestoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(it) }
    }

    private var pinnedEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 应用顶栏颜色
        UiUtils.applyToolbarColor(this)

        // 应用壁纸
        UiUtils.applyWallpaper(binding.root, this)

        // 初始化搜索历史管理
        searchHistoryManager = SearchHistoryManager(this)

        // 初始化常搜管理
        pinnedSearchManager = PinnedSearchManager(this)

        // 注册应用选择结果回调
        appSelectionResultLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                val packageName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_PACKAGE_NAME) ?: ""
                val appName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_APP_NAME) ?: ""
                onAppSelectedGlobal?.invoke(packageName, appName)
            }
        }

        setupRecyclerView()
        setupSearchFunction()

        lifecycleScope.launch {
            searchViewModel.groupedItems.collectLatest {
                groupedAdapter.submitList(it)
            }
        }

        setupSelectAllButton()

        refreshSearchHistorySuggestions()
        refreshPinnedChips()
    }

    override fun onResume() {
        super.onResume()
        UiUtils.applyToolbarColor(this)
        // 同步搜索按钮颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        binding.buttonSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorStr))

        UiUtils.applyWallpaper(binding.root, this)

        refreshSearchHistorySuggestions()
        refreshPinnedChips()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_pin -> {
                val query = binding.editTextSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    pinnedSearchManager.addPin(query)
                    refreshPinnedChips()
                    Toast.makeText(this, "已固定", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入内容后再固定", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.menu_plus -> {
                // 二选一弹窗
                AlertDialog.Builder(this)
                    .setTitle("请选择要添加的内容")
                    .setItems(arrayOf("创建分组", "创建链接")) { _, which ->
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
        searchViewModel.allUrls.value?.forEach { url ->
            val urlObject = JSONObject().apply {
                put("id", url.id)
                put("name", url.name)
                put("urlPattern", url.urlPattern)
                put("isEnabled", url.isEnabled)
                put("orderIndex", url.orderIndex)
                put("groupId", url.groupId)
                // 添加包名到备份数据中
                put("packageName", url.packageName)
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
                    groupId = urlObject.getLong("groupId"),
                    // 尝试从备份数据中恢复包名，如果不存在则使用空字符串
                    packageName = if (urlObject.has("packageName"))
                        urlObject.getString("packageName")
                    else
                        ""
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
                    // 当点击分组时，获取该分组内所有用的链接并打开
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
                if (item is SearchUrl) showDeleteConfirmationDialog(item)
                else if (item is UrlGroup) showDeleteGroupDialog(item)
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
                currentSelectedAppNameTextView?.text = "已选择: $appName"
                currentSelectedAppNameTextView?.visibility = View.VISIBLE
            } else {
                currentSelectedAppNameTextView?.visibility = View.GONE
            }
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            appSelectionResultLauncher.launch(Intent(this, com.example.aggregatesearch.activities.AppSelectionActivity::class.java))
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

    private fun showEditUrlDialog(searchUrl: SearchUrl) {
        val dialogBinding = com.example.aggregatesearch.databinding.DialogEditUrlBinding.inflate(LayoutInflater.from(this))

        // 预填充现有值
        dialogBinding.editTextUrlName.setText(searchUrl.name as CharSequence)
        dialogBinding.editTextUrlPattern.setText(searchUrl.urlPattern as CharSequence)

        // 预填充包名，如果存在
        var selectedPackageName = searchUrl.packageName
        if (selectedPackageName.isNotEmpty()) {
            dialogBinding.editTextPackageName.setText(selectedPackageName as CharSequence)

            // 获取应用名称
            val appPackageManager = AppPackageManager(this)
            val appName = appPackageManager.getAppNameByPackage(selectedPackageName)
            if (appName != null) {
                dialogBinding.textViewSelectedAppName.text = "已选择: $appName"
                dialogBinding.textViewSelectedAppName.visibility = View.VISIBLE
            }
        }

        // 设置选择应用按钮点击事件
        currentPackageNameEditText = dialogBinding.editTextPackageName
        currentSelectedAppNameTextView = dialogBinding.textViewSelectedAppName
        onAppSelectedGlobal = { packageName: String, appName: String ->
            selectedPackageName = packageName
            currentPackageNameEditText?.setText(packageName)
            if (packageName.isNotEmpty()) {
                currentSelectedAppNameTextView?.text = "已选择: $appName"
                currentSelectedAppNameTextView?.visibility = View.VISIBLE
            } else {
                currentSelectedAppNameTextView?.visibility = View.GONE
            }
        }

        dialogBinding.btnSelectApp.setOnClickListener {
            appSelectionResultLauncher.launch(Intent(this, com.example.aggregatesearch.activities.AppSelectionActivity::class.java))
        }

        // 同步按钮颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val tint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorStr))
        dialogBinding.btnSelectApp.backgroundTintList = tint
        dialogBinding.btnSelectApp.setTextColor(android.graphics.Color.WHITE)
        dialogBinding.btnTestUrl.backgroundTintList = tint
        dialogBinding.btnTestUrl.setTextColor(android.graphics.Color.WHITE)

        dialogBinding.btnTestUrl.setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim().ifEmpty { "测试" }
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()
            val tempUrl = searchUrl.copy(name = name, urlPattern = pattern, packageName = selectedPackageName)
            UrlLauncher.launchSearchUrls(this, binding.editTextSearch.text.toString().trim(), listOf(tempUrl))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑搜索链接")
            .setView(dialogBinding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.editTextUrlName.text.toString().trim()
            val pattern = dialogBinding.editTextUrlPattern.text.toString().trim()

            if (name.isNotEmpty()) {
                val updatedUrl = searchUrl.copy(
                    name = name,
                    urlPattern = pattern,
                    packageName = selectedPackageName
                )
                searchViewModel.updateUrl(updatedUrl)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditGroupDialog(group: UrlGroup) {
        val dialogBinding = com.example.aggregatesearch.databinding.DialogEditGroupBinding.inflate(LayoutInflater.from(this))

        // 预填充现有值
        dialogBinding.editTextGroupName.setText(group.name)

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
                val updatedGroup = group.copy(name = name)
                searchViewModel.updateGroup(updatedGroup)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "分组名称不能为空", Toast.LENGTH_SHORT).show()
            }
        }
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
            // 保存历史
            if (searchHistoryManager.isHistoryEnabled()) {
                searchHistoryManager.addQuery(searchQuery)
                val autoText = binding.editTextSearch as? AutoCompleteTextView
                autoText?.threshold = 0
                val adapter = autoText?.adapter as? ArrayAdapter<String>
                if (adapter != null) {
                    adapter.clear()
                    adapter.addAll(searchHistoryManager.getHistory())
                    adapter.notifyDataSetChanged()
                } else {
                    autoText?.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, searchHistoryManager.getHistory()))
                }
            }
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

    private fun setupSelectAllButton() {
        val autoText = binding.editTextSearch as? AutoCompleteTextView
        autoText?.apply {
            threshold = 0 // 点击即弹出
            if (searchHistoryManager.isHistoryEnabled()) {
                setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, searchHistoryManager.getHistory()))
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showDropDown()
                }
            }
            setOnClickListener {
                showDropDown()
            }
        }

        binding.buttonSelectAll.setOnClickListener {
            isAllSelected = !isAllSelected
            // 更新图标
            val iconRes = if (isAllSelected) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
            binding.buttonSelectAll.setImageResource(iconRes)

            // 更新所有链接选中状态
            searchViewModel.setAllUrlsEnabled(isAllSelected)
        }
    }

    /** 刷新搜索历史下拉建议 */
    private fun refreshSearchHistorySuggestions() {
        val autoText = binding.editTextSearch as? AutoCompleteTextView ?: return
        if (searchHistoryManager.isHistoryEnabled()) {
            autoText.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, searchHistoryManager.getHistory()))
        } else {
            autoText.setAdapter(null)
        }
    }

    /** 刷新固定关键词 Chips */
    private fun refreshPinnedChips() {
        val container = binding.containerPinned
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val pinned = pinnedSearchManager.getPinned()
        for (word in pinned) {
            val chip = com.google.android.material.chip.Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = word
                isCloseIconVisible = pinnedEditMode
                setOnClickListener {
                    if (pinnedEditMode) {
                        // 删除
                        pinnedSearchManager.removePin(word)
                        refreshPinnedChips()
                    } else {
                        binding.editTextSearch.setText(word)
                        binding.editTextSearch.setSelection(word.length)
                        performSearch()
                    }
                }
                setOnLongClickListener {
                    pinnedEditMode = !pinnedEditMode
                    refreshPinnedChips()
                    true
                }
            }
            container.addView(chip)
        }
        binding.scrollViewPinned.visibility = if (pinned.isEmpty()) View.GONE else View.VISIBLE
    }
}
