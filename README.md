# 程序功能介绍

这是一个可以调用app内搜索的安卓聚合搜索应用，允许用户通过单次输入同时打开多个搜索链接。

## 使用方法

1. 在主界面的搜索框中输入要查询的内容，点击搜索按钮，应用将同时打开所有已勾选链接。
2. 可以单独点击某个链接以打开。
3. 可以添加自定义链接，会自动存储，也可以备份和恢复。
4. 第一次添加的app链接，需要先点击一次以获取app跳转权限。

## 开发原因

由于大陆app对手机网页端支持较差，常见的聚合搜索无法良好使用，开发此app以解决痛点。

## 更新计划和需求

1. 目前在 v1.1 版本，后续预计增加更多预设链接或链接仓库，如公众号搜索、微博搜索。
2. 预计增加网盘搜索、开发者搜索（百度开发者、阿里云开发者、github、掘金开发者、博客园、CSDN等搜索）作为仓库供选择。
3. 如有需求，会尝试支持ios系统。

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
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── aggregatesearch/
│       │               ├── data/                      # 数据层
│       │               │   ├── Migrations.kt          # 数据库迁移
│       │               │   ├── SearchUrl.kt           # 搜索链接实体
│       │               │   ├── SearchUrlDao.kt        # 数据访问对象
│       │               │   ├── SearchUrlDatabase.kt   # Room 数据库
│       │               │   ├── SearchUrlRepository.kt # 仓库层
│       │               │   └── UrlGroup.kt            # URL分组
│       │               ├── GroupedSearchUrlAdapter.kt # 分组的URL适配器
│       │               ├── MainActivity.kt            # 主活动
│       │               ├── SearchApplication.kt       # 应用类
│       │               ├── SearchUrlAdapter.kt        # URL适配器
│       │               ├── SearchViewModel.kt         # ViewModel
│       │               └── UrlLauncher.kt             # URL启动器
│       └── res/                                       # 资源文件
```

## 核心组件解析

### 数据层 (data/)
- **SearchUrl.kt**：定义搜索URL的数据结构
- **SearchUrlDao.kt**：定义数据库操作接口
- **SearchUrlDatabase.kt**：Room数据库的设置和实例化
- **SearchUrlRepository.kt**：作为数据源和ViewModel之间的中介
- **Migrations.kt**：处理数据库版本迁移
- **UrlGroup.kt**：管理URL分组功能

### UI层
- **MainActivity.kt**：主界面，包含搜索框和链接列表
- **SearchViewModel.kt**：管理UI相关的数据和业务逻辑
- **SearchUrlAdapter.kt**：为RecyclerView提供数据绑定
- **GroupedSearchUrlAdapter.kt**：处理分组显示的URL适配器

### 工具类
- **UrlLauncher.kt**：处理URL的打开和跳转逻辑
- **SearchApplication.kt**：应用级配置和依赖注入