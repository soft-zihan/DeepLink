# DeepLink 聚合搜索

这是一个深度定制的安卓聚合搜索应用，允许用户通过单次输入，同时调用多个App的内部搜索或打开指定网页。

## 核心功能

1.  **一键多平台搜索**：在主界面输入关键词，一键启动所有选中的链接。
2.  **深度定制**：自由添加、编辑、删除和排序搜索链接及分组。支持标准的网页链接和应用的 `Scheme` 链接。
3.  **灵活控制**：可单独勾选或取消某个链接，也可通过分组批量控制。
4.  **数据管理**：支持本地备份和从文件恢复所有配置，数据安全不丢失。
5.  **个性化**：
    *   **常搜固定**：将常用搜索词固定在主页，方便快速点击。
    *   **主题与壁纸**：支持浅色/深色/跟随系统模式，并可为不同主题设置专属壁纸和透明度。
    *   **颜色定制**：顶栏颜色自由更换，按钮颜色随之同步。

## 使用技巧

*   **链接格式**：添加链接时，用 `%s` 代替搜索词。例如：`bilibili://search/%s` 或 `https://www.baidu.com/s?wd=%s`。
*   **空链接启动应用**：如果链接地址留空，但绑定了应用包名，点击时将直接启动该应用。
*   **排序**：在主界面长按链接或分组，即可拖动排序。
*   **删除常搜**：在主界面长按已固定的关键词，进入编辑模式，点击叉号即可删除。
*   **触发分组**：点击分组名区域，可一次性触发该分组下所有已勾选的链接。

## 更新计划

1. 目前在 v1.3 版本，后续预计增加更多预设链接，比如网盘搜索、开发人员搜索等。微信公众号、贴吧暂未反编译成功。有计划引入AI搜索功能，搜集汇总所选平台的搜索结果。
2. 可能开发用于PC的web端，在同一个界面聚合所有搜索结果或其他链接。
3. scheme URL有小概率随着对应软件的版本更新变化，如果失效可以提交issue。如能提供感之不尽。

## 技术栈与构建

*   **语言**: Kotlin
*   **架构**: MVVM (ViewModel, Repository, Room)
*   **核心组件**: AndroidX, Coroutines, StateFlow, LiveData, Room, Material Design Components
*   **构建**: 使用 Android Studio 打开项目，通过 Gradle (`./gradlew assembleRelease`) 构建。请确保已按需配置 `keystore.properties`。


## 开发原因

由于大陆app对手机网页端支持较差，常见的聚合搜索无法良好使用，开发此app以解决痛点。

## 功能特点

- 聚合搜索
- 支持自定义搜索链接
- 支持App链接（如 bilibili://search/%s）和网页链接（如 https://www.baidu.com/s?wd=%s）
- 可选择性启用/禁用特定搜索链接
- 在首次启动时预设了常用搜索平台

## 开发环境

- Android Studio
- Kotlin
- Gradle
- AndroidX
- Room 数据库 (用于持久化存储搜索链接)

## 如何构建

1.  克隆仓库到本地。
2.  在 Android Studio 中打开项目。
3.  确保已配置 `local.properties` 文件，其中包含 SDK 路径。
4.  如果需要构建发行版，请确保 `keystore.properties` 文件已正确配置，并且 `my-release-key.jks` 文件位于项目根目录。
5.  通过 Android Studio 的构建菜单构建项目，或者在项目根目录运行以下命令：
    *   调试版本: `./gradlew assembleDebug`
    *   发行版本: `./gradlew assembleRelease`

# 项目文件介绍

## 技术栈

- **编程语言**：Kotlin
- **开发环境**：Android Studio
- **构建工具**：Gradle
- **SDK版本**：
    - 编译 SDK：34
    - 最低 SDK：24
    - 目标 SDK：34
- **架构组件**：
    - AndroidX
    - ViewModel & LiveData
    - Room 数据库 (用于持久化存储搜索链接)
- **UI组件**：
    - ViewBinding
    - RecyclerView
    - ConstraintLayout
    - Material Design

## 项目结构

```
app/
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── example/
        │           └── aggregatesearch/
        │               ├── activities/
        │               │   └── AppSelectionActivity.kt      # 应用选择界面
        │               ├── adapters/
        │               │   ├── AppAdapter.kt                # 应用列表适配器
        │               │   └── GroupedSearchUrlAdapter.kt   # 主页分组链接适配器
        │               ├── data/
        │               │   ├── Migrations.kt              # 数据库迁移脚本
        │               │   ├── SearchUrl.kt               # 链接数据实体
        │               │   ├── SearchUrlDao.kt            # 数据库访问对象 (DAO)
        │               │   ├── SearchUrlDatabase.kt       # Room 数据库定义
        │               │   ├── SearchUrlRepository.kt     # 数据仓库
        │               │   └── UrlGroup.kt                # 分组数据实体
        │               ├── preferences/
        │               │   ├── ThemeTogglePreference.kt   # 自定义主题切换控件
        │               │   └── ToolbarColorPreference.kt  # 自定义顶栏颜色选择控件
        │               ├── utils/
        │               │   ├── AppPackageManager.kt       # 获取已安装应用列表
        │               │   ├── PinnedSearchManager.kt     # 常搜词管理器
        │               │   ├── SearchHistoryManager.kt    # 搜索历史管理器
        │               │   └── UiUtils.kt                 # UI工具类（颜色、壁纸）
        │               ├── MainActivity.kt                # 主活动
        │               ├── SearchApplication.kt           # Application 类，用于初始化
        │               ├── SearchViewModel.kt             # 主活动的 ViewModel
        │               ├── SettingsActivity.kt            # 设置活动
        │               └── UrlLauncher.kt                 # URL 启动器
        └── res/
            ├── drawable/                                # 图片资源
            ├── layout/                                  # 布局文件
            ├── menu/                                    # 菜单资源
            ├── values/                                  # 颜色、字符串、样式等
            └── xml/                                     # Preference 配置文件
```

## 核心组件解析

### 数据层 (data/)
- **SearchUrl.kt, UrlGroup.kt**：定义链接和分组的数据实体。
- **SearchUrlDao.kt**：定义数据库操作接口 (CRUD)。
- **SearchUrlDatabase.kt**：Room数据库的设置和实例化。
- **SearchUrlRepository.kt**：统一的数据源，隔离ViewModel和数据来源。

### UI层 & 交互
- **MainActivity.kt**: 主界面，管理链接列表、搜索、常搜词等核心交互。
- **SettingsActivity.kt**: 设置页，使用 PreferenceFragmentCompat 管理所有设置项。
- **AppSelectionActivity.kt**: 独立的应用选择列表界面。
- **GroupedSearchUrlAdapter.kt**: 为主页 RecyclerView 提供数据绑定，处理分组和链接的显示、拖拽排序、点击事件等。
- **preferences/**: 包含自定义的 `Preference` 控件，如 `ThemeTogglePreference`，用于在设置页实现非标准的UI（如并排按钮）。

### ViewModel
- **SearchViewModel.kt**: 遵循MVVM架构，管理UI相关的数据和业务逻辑，通过 `StateFlow` 将数据流暴露给UI层。

### 工具类 (utils/)
- **UrlLauncher.kt**: 处理URL的打开和跳转逻辑。
- **SearchHistoryManager.kt**: 管理搜索历史记录。
- **PinnedSearchManager.kt**: 管理固定的常搜关键词。
- **UiUtils.kt**: 提供UI相关的辅助功能，如动态应用顶栏颜色和壁纸。
- **AppPackageManager.kt**: 获取设备上已安装的应用列表。