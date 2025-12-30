<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

# 聚合搜索应用开发指南

该项目是一个安卓聚合搜索应用，功能包括：
- 单一搜索框同时触发多个搜索平台
- 支持自定义搜索链接
- 链接形式支持：App链接(bilibili://search/%s)和网页链接(https://www.baidu.com/s?wd=%s)
- 通过复选框选择要搜索的平台

## 代码结构

- `data/`: 包含数据模型和Room数据库相关类
- `MainActivity.kt`: 主界面逻辑
- `SearchViewModel.kt`: 用于管理UI状态和数据
- `SearchUrlAdapter.kt`: RecyclerView适配器
- `UrlLauncher.kt`: 处理URL启动逻辑

## 注意事项

- 应用使用Room数据库存储自定义搜索链接
- 使用Intent.ACTION_VIEW处理链接打开
- 首次启动时会添加一些默认搜索链接
