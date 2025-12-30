# DeepLink 聚合搜索应用项目结构文档

## 1. 项目概述
DeepLink 是一个安卓聚合搜索应用，允许用户通过一个搜索框同时在多个平台（App 或网页）进行搜索。支持自定义搜索链接、分组管理、内置浏览器、主题切换等功能。

## 2. 核心目录结构与关键函数

### 2.1 `com.example.aggregatesearch` (根包)
- **`MainActivity.kt`**: 应用主界面。
    - `onCreate()`: 初始化 UI、工具类、管理器及浏览器。
    - `observeDataChanges()`: 监听 `ViewModel` 数据流并更新 UI。
    - `handleBackPress()`: 处理返回键逻辑（浏览器回退或退出）。
- **DeepSeek（内嵌 Web）集成**
    - 主界面搜索框下方新增 DeepSeek 区域（可折叠/展开且持久化），用于承载 `https://chat.deepseek.com/`。
    - 搜索动作会额外同步触发 DeepSeek 新对话并自动提问（与普通平台搜索并行）。
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

### 2.2 `com.example.aggregatesearch.deepseek/` (DeepSeek 内嵌模块)
- **`DeepSeekManager.kt`**: DeepSeek WebView 管理器。
    - `initialize()`: 延迟初始化 WebView（首次需要时创建）。
    - `toggleExpand()` / `updateExpandState()`: 折叠/展开与状态持久化。
    - `sendQuery(query)` / `executeSearch(query)`: 打开新对话并自动填入发送。
    - `setupCookies()` / `saveCookie(cookie)` / `saveCookieFromPage(url)`: Cookie 管理与自动保存。
    - `checkLoginStatusAndInject(url)`: 检测登录态并更新 UI 状态文本（不再做 CSS 注入）。
    - WebView 滚动适配：通过 `requestDisallowInterceptTouchEvent(true)` 避免父容器拦截手势导致无法上下滑动。

### 2.3 `data/` (数据持久化)
- **`SearchUrl.kt`**: 搜索链接实体类。
- **`UrlGroup.kt`**: 分组实体类。
- **`SearchUrlDao.kt`**: Room DAO（数据库访问接口）。
    - 为 `SearchUrlRepository` 提供 URL/分组的查询、插入、更新、删除等数据访问方法。
- **`SearchUrlRepository.kt`**: 数据仓库。
    - `initializeDatabaseIfFirstLaunch()`: 首次启动时注入默认搜索数据。
    - `allUrls/allGroups`: 提供数据库的 `Flow` 数据流。

### 2.4 `ui/` (UI 功能模块化)
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
        - 额外：调用 `MainActivity.sendQueryToDeepSeek(query)`，使 DeepSeek 同步创建新对话并提问。
    - `setupPinnedSearches()`: 初始化固定搜索词列表。

### 2.5 `browser/` (内置浏览器)
- **`BrowserManager.kt`**: 浏览器生命周期管理。
    - `addWebTab()`: 新建浏览器标签页。
    - `createWebView()`: 配置 WebView 设置（JS、UA、Cookie 等）。
- **`BrowserTab.kt`**: 标签页实体。

### 2.6 `utils/` (工具类)
- **`IconLoader.kt`**: 图标加载工具。
    - `loadIcon()`: 智能加载（网络/应用/文字图标）。
- **`BackupRestoreManager.kt`**: 备份恢复。
    - `backupData()` / `restoreData()`: JSON 格式导出导入。
- **`SearchHistoryManager.kt`**: 搜索历史管理。
- **`PinnedSearchManager.kt`**: 固定搜索词管理。

### 2.7 `activities/` & `dialogs/` (其他界面)
- **`AppSelectionActivity.kt`**: 应用选择界面。
- **`SettingsActivity.kt`**: 设置界面。
    - DeepSeek 设置项：启用开关、登录引导（回主界面在 WebView 登录）、清除 DeepSeek 数据（Cookie/WebStorage/偏好）。

### 2.8 `preferences/` (自定义偏好设置组件)
- 包含 `ThemePreference`、`ToolbarColorPreference` 等自定义 UI 组件。

## 2.9 关键布局与资源
- **`app/src/main/res/layout/activity_main.xml`**
    - 在固定搜索词列表下方 include DeepSeek 区域，并调整列表/浏览器容器的布局锚点。
- **`app/src/main/res/layout/layout_deepseek.xml`**
    - DeepSeek 可折叠区域布局：标题栏（DeepSeek/展开箭头/状态/进度）+ WebView 容器。
- **`app/src/main/res/xml/preferences.xml`**
    - 新增 DeepSeek 分类配置（启用、登录引导、清理数据）。

## 3. 优化建议关注点
- **性能**: `GroupedSearchUrlAdapter` 中的 `notifyDataSetChanged()` 调用。
- **稳定性**: 数据库迁移与初始化逻辑。
- **扩展性**: `UrlLauncher` 对更多协议的支持。
- **UI/UX**: 列表滑动流畅度、图标加载速度。
- **WebView 交互**: DeepSeek 内嵌 WebView 需要避免父容器拦截滑动，确保可上下滚动。

