package com.example.aggregatesearch

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aggregatesearch.browser.BrowserManager
import com.example.aggregatesearch.browser.TabAdapter
import com.example.aggregatesearch.databinding.ActivityMainBinding
import com.example.aggregatesearch.ui.appselection.AppSelectionManager
import com.example.aggregatesearch.ui.dialogs.DialogManager
import com.example.aggregatesearch.ui.menu.MenuManager
import com.example.aggregatesearch.ui.recyclerview.RecyclerViewManager
import com.example.aggregatesearch.ui.search.SearchFunctionManager
import com.example.aggregatesearch.utils.AppPreferences
import com.example.aggregatesearch.utils.BackupRestoreManager
import com.example.aggregatesearch.utils.PinnedSearchManager
import com.example.aggregatesearch.utils.SearchHistoryManager
import com.example.aggregatesearch.utils.UiUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((application as SearchApplication).repository)
    }

    // 功能管理器
    private lateinit var searchFunctionManager: SearchFunctionManager
    private lateinit var recyclerViewManager: RecyclerViewManager
    private lateinit var dialogManager: DialogManager
    private lateinit var menuManager: MenuManager
    private lateinit var appSelectionManager: AppSelectionManager
    private lateinit var backupRestoreManager: BackupRestoreManager

    // 内置浏览器相关
    private lateinit var browserManager: BrowserManager
    private lateinit var tabAdapter: TabAdapter

    // 基础工具类
    private lateinit var searchHistoryManager: SearchHistoryManager
    private lateinit var pinnedSearchManager: PinnedSearchManager
    private lateinit var appPreferences: AppPreferences

    // 应用选择结果回调
    private lateinit var appSelectionResultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 设置Toolbar
            setSupportActionBar(binding.toolbar)

            // 应用UI主题
            UiUtils.applyToolbarColor(this)
            UiUtils.applyWallpaper(binding.root, this)

            // 初始化基础工具类
            initializeUtilities()

            // 初始化功能管理器
            initializeManagers()

            // 初始化内置浏览器
            initializeBrowser()

            // 设置各个功能模块
            setupFunctionModules()

            // 观察数据变化
            observeDataChanges()

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate", e)
            android.widget.Toast.makeText(this, "发生错误，请查看日志: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        UiUtils.applyToolbarColor(this)
        // 同步搜索按钮颜色
        val colorStr = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        binding.buttonSearch.backgroundTintList = ColorStateList.valueOf(colorStr.toColorInt())

        UiUtils.applyWallpaper(binding.root, this)

        // 刷新分组UI以应用透明度
        recyclerViewManager.notifyDataSetChanged()

        // 刷新数据
        searchFunctionManager.refreshSearchHistorySuggestions()
        searchFunctionManager.refreshPinnedSearches()
    }

    private fun initializeUtilities() {
        searchHistoryManager = SearchHistoryManager(this)
        pinnedSearchManager = PinnedSearchManager(this)
        appPreferences = AppPreferences(this)
    }

    private fun initializeManagers() {
        // 初始化应用选择结果回调
        appSelectionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val packageName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_PACKAGE_NAME) ?: ""
                val appName = data?.getStringExtra(com.example.aggregatesearch.activities.AppSelectionActivity.EXTRA_APP_NAME) ?: ""
                appSelectionManager.handleAppSelectionResult(packageName, appName)
            }
        }

        // 初始化各个管理器
        appSelectionManager = AppSelectionManager(this, appSelectionResultLauncher)
        backupRestoreManager = BackupRestoreManager(this, searchViewModel)

        searchFunctionManager = SearchFunctionManager(
            this, binding, searchViewModel, searchHistoryManager, pinnedSearchManager
        )

        recyclerViewManager = RecyclerViewManager(
            context = this,
            binding = binding,
            searchViewModel = searchViewModel,
            onEditItemClicked = { item ->
                when (item) {
                    is com.example.aggregatesearch.data.SearchUrl -> dialogManager.showEditUrlDialog(item)
                    is com.example.aggregatesearch.data.UrlGroup -> dialogManager.showEditGroupDialog(item)
                }
            },
            onAddUrlClicked = { group ->
                dialogManager.showAddUrlDialog(group)
            },
            onIconStateChanged = { url ->
                searchViewModel.updateUrlStateInBackground(url)
            },
            getCurrentSearchQuery = { searchFunctionManager.getCurrentSearchQuery() },
            onIconOrderChanged = { urls ->
                searchViewModel.updateIconOrderInGroup(urls)
            }
        )

        dialogManager = DialogManager(
            context = this,
            searchViewModel = searchViewModel,
            appSelectionResultLauncher = appSelectionResultLauncher,
            onAppSelected = { packageName, appName ->
                appSelectionManager.handleAppSelectionResult(packageName, appName)
            },
            onRefreshIconsForGroup = { groupId ->
                recyclerViewManager.refreshIconsForGroup(groupId)
            },
            getCurrentSearchQuery = { searchFunctionManager.getCurrentSearchQuery() },
            onExitEditMode = {
                // 编辑保存后自动退出编辑模式
                menuManager.forceExitEditMode()
                recyclerViewManager.setEditMode(false)
                searchFunctionManager.setPinnedEditMode(false)
            }
        )

        menuManager = MenuManager(
            context = this,
            onEditModeToggle = {
                val isEditMode = menuManager.getEditMode()
                recyclerViewManager.setEditMode(isEditMode)
                searchFunctionManager.setPinnedEditMode(isEditMode)
            },
            onPinCurrentSearch = {
                searchFunctionManager.addPinnedSearch()
            },
            onShowAddDialog = {
                showAddOptionsDialog()
            },
            onShowHelpDialog = {
                showHelpDialog()
            }
        )

        // The onForceRefreshAllIconStates callback is no longer needed.
    }

    private fun initializeBrowser() {
        browserManager = BrowserManager(this)

        // 设置标签适配器
        tabAdapter = TabAdapter(
            onTabClick = { tab ->
                browserManager.switchToTab(tab)
            },
            onTabDoubleClick = { tab ->
                browserManager.removeTab(tab)
            },
            onTabClose = { tab ->
                browserManager.removeTab(tab)
            }
        )

        // 设置标签栏
        binding.recyclerViewTabs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = tabAdapter
        }

        // 观察标签变化
        browserManager.tabs.observe(this) { tabs ->
            tabAdapter.updateTabs(tabs)
            // 显示或隐藏标签栏
            updateTabBarVisibility()
            // 更新回退按钮
            updateBackButtonVisibility()
        }

        // 观察当前标签变化
        browserManager.currentTab.observe(this) { currentTab ->
            currentTab?.let {
                tabAdapter.setSelectedTab(it.id)
                updateWebViewContainer(it)
                // 更新标题
                supportActionBar?.title = if (it.isMainTab) "" else it.title
            }
        }
    }

    private fun updateTabBarVisibility() {
        val hasWebTabs = browserManager.hasWebTabs()
        binding.recyclerViewTabs.visibility = if (hasWebTabs) View.VISIBLE else View.GONE
    }

    private fun updateWebViewContainer(currentTab: com.example.aggregatesearch.browser.BrowserTab) {
        if (currentTab.isMainTab) {
            // 显示主界面
            binding.webViewContainer.visibility = View.GONE
            binding.recyclerViewUrls.visibility = View.VISIBLE
            binding.searchContainer.visibility = View.VISIBLE
            binding.recyclerViewPinned.visibility = View.VISIBLE
        } else {
            // 显示WebView
            binding.webViewContainer.visibility = View.VISIBLE
            binding.recyclerViewUrls.visibility = View.GONE
            binding.searchContainer.visibility = View.GONE
            binding.recyclerViewPinned.visibility = View.GONE

            // 将当前标签的WebView添加到容器中
            binding.webViewContainer.removeAllViews()
            val webView = browserManager.getWebView(currentTab.id)
            webView?.let {
                // 确保WebView没有父容器
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                binding.webViewContainer.addView(it)
            }
        }
    }

    private fun updateBackButtonVisibility() {
        // val shouldShowBack = browserManager.hasWebTabs() && !browserManager.currentTab.value?.isMainTab == true
        // 这里需要在菜单创建后更新回退按钮的可见性
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return menuManager.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val shouldShowBack = browserManager.hasWebTabs() && browserManager.currentTab.value?.isMainTab == false
        menu.findItem(R.id.menu_browser_back)?.isVisible = shouldShowBack

        // 根据是否在浏览器视图中，决定是否显示其他菜单项
        val isInBrowserView = shouldShowBack
        menu.findItem(R.id.menu_plus)?.isVisible = !isInBrowserView
        menu.findItem(R.id.menu_edit_mode)?.isVisible = !isInBrowserView
        menu.findItem(R.id.menu_pin)?.isVisible = !isInBrowserView
        menu.findItem(R.id.menu_settings)?.isVisible = !isInBrowserView
        menu.findItem(R.id.menu_help)?.isVisible = !isInBrowserView

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_browser_back -> {
                if (browserManager.canGoBack()) {
                    browserManager.goBack()
                } else {
                    // 如果WebView无法回退，切换到主标签
                    val mainTab = browserManager.tabs.value?.firstOrNull { it.isMainTab }
                    mainTab?.let { browserManager.switchToTab(it) }
                }
                true
            }
            else -> menuManager.onOptionsItemSelected(item)
        }
    }

    private fun setupFunctionModules() {
        recyclerViewManager.setupRecyclerView()
        searchFunctionManager.setupSearchFunction()
        searchFunctionManager.setupPinnedSearches()
        searchFunctionManager.setupSelectAllButton { selected ->
            searchViewModel.setAllSelected(selected)
        }
    }

    private fun observeDataChanges() {
        lifecycleScope.launch {
            searchViewModel.groupedItems.collectLatest { items ->
                recyclerViewManager.submitList(items)
            }
        }

        lifecycleScope.launch {
            searchViewModel.allUrls.collectLatest { urls ->
                val allSelected = urls.isNotEmpty() && urls.all { it.isEnabled }
                searchFunctionManager.updateSelectAllButtonState(allSelected)
            }
        }
    }

    private fun showAddOptionsDialog() {
        AlertDialog.Builder(this)
            .setTitle("请选择要添加的内容")
            .setItems(arrayOf("创建分组", "创建链接")) { _, which ->
                if (which == 0) {
                    dialogManager.showAddGroupDialog()
                } else {
                    dialogManager.showAddUrlDialog()
                }
            }
            .show()
    }

    private fun showHelpDialog() {
        menuManager.createHelpDialog().show()
    }

    // 保留备份导入导出功能的接口，供设置界面调用
    fun exportBackup(uri: Uri) {
        backupRestoreManager.exportBackup(uri)
    }

    fun importBackup(uri: Uri) {
        backupRestoreManager.importBackup(uri)
    }

    /**
     * 供外部调用的方法，用于在内置浏览器中打开URL
     */
    fun openInBuiltInBrowser(url: String) {
        val newTab = browserManager.addWebTab(url)
        browserManager.switchToTab(newTab)
    }
}
