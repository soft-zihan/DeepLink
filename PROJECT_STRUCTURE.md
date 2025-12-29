# DeepLink 聚合搜索应用项目结构文档

## 1. 项目概述
DeepLink 是一个安卓聚合搜索应用，允许用户通过一个搜索框同时在多个平台（App 或网页）进行搜索。支持自定义搜索链接、分组管理、内置浏览器、主题切换等功能。

## 2. 核心目录结构与关键函数

### 2.1 `com.example.aggregatesearch` (根包)
- **`MainActivity.kt`**: 应用主界面。
    - `onCreate()`: 初始化 UI、工具类、管理器及浏览器。
    - `observeDataChanges()`: 监听 `ViewModel` 数据流并更新 UI。
    - `handleBackPress()`: 处理返回键逻辑（浏览器回退或退出）。
- **`SearchViewModel.kt`**: 核心 ViewModel。
    - `insert/updateUrl/delete()`: 搜索链接的增删改。
    - `addGroup/deleteGroup()`: 分组的增删。
    - `updateUrlsOrder()`: 处理拖拽排序后的位置持久化。
    - `groupedItems`: 暴露聚合后的分组数据流。
- **`UrlLauncher.kt`**: 链接启动器。
    - `launchSearchUrls()`: 核心启动逻辑，处理多链接并发启动。
    - `formatUrl()`: 替换 `%s` 占位符并进行 URL 编码。
- **`GroupedSearchUrlAdapter.kt`**: 主界面分组列表适配器。
    - `setEditMode()`: 切换编辑/普通模式。
    - `onBindViewHolder()`: 绑定分组数据及嵌套的图标列表。

### 2.2 `data/` (数据持久化)
- **`SearchUrl.kt`**: 搜索链接实体类。
- **`UrlGroup.kt`**: 分组实体类。
- **`SearchUrlRepository.kt`**: 数据仓库。
    - `initializeDatabaseIfFirstLaunch()`: 首次启动时注入默认搜索数据。
    - `allUrls/allGroups`: 提供数据库的 `Flow` 数据流。

### 2.3 `ui/` (UI 功能模块化)
- **`appselection/AppSelectionManager.kt`**: 处理应用选择逻辑。
- **`dialogs/DialogManager.kt`**: 管理所有弹窗。
    - `showAddGroupDialog()` / `showAddUrlDialog()`: 添加分组/链接弹窗。
    - `showEditUrlDialog()` / `showEditGroupDialog()`: 编辑弹窗。
- **`menu/MenuManager.kt`**: 管理 Toolbar 菜单。
    - `setupMenu()`: 初始化菜单项点击事件。
- **`recyclerview/RecyclerViewManager.kt`**: 管理主界面列表。
    - `setupRecyclerView()`: 初始化 Flexbox 布局及适配器。
- **`search/SearchFunctionManager.kt`**: 管理搜索功能。
    - `performSearch()`: 执行搜索，记录历史并调用 `UrlLauncher`。
    - `setupPinnedSearches()`: 初始化固定搜索词列表。

### 2.4 `browser/` (内置浏览器)
- **`BrowserManager.kt`**: 浏览器生命周期管理。
    - `addWebTab()`: 新建浏览器标签页。
    - `createWebView()`: 配置 WebView 设置（JS、UA、Cookie 等）。
- **`BrowserTab.kt`**: 标签页实体。

### 2.5 `utils/` (工具类)
- **`IconLoader.kt`**: 图标加载工具。
    - `loadIcon()`: 智能加载（网络/应用/文字图标）。
- **`BackupRestoreManager.kt`**: 备份恢复。
    - `backupData()` / `restoreData()`: JSON 格式导出导入。
- **`SearchHistoryManager.kt`**: 搜索历史管理。
- **`PinnedSearchManager.kt`**: 固定搜索词管理。

### 2.6 `activities/` & `dialogs/` (其他界面)
- **`AppSelectionActivity.kt`**: 应用选择界面。
- **`SettingsActivity.kt`**: 设置界面。

### 2.7 `preferences/` (自定义偏好设置组件)
- 包含 `ThemePreference`、`ToolbarColorPreference` 等自定义 UI 组件。

## 3. 优化建议关注点
- **性能**: `GroupedSearchUrlAdapter` 中的 `notifyDataSetChanged()` 调用。
- **稳定性**: 数据库迁移与初始化逻辑。
- **扩展性**: `UrlLauncher` 对更多协议的支持。
- **UI/UX**: 列表滑动流畅度、图标加载速度。

