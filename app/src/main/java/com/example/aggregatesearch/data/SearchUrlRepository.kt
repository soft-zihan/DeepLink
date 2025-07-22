package com.example.aggregatesearch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull // Required for getting a single value from Flow

class SearchUrlRepository(private val searchUrlDao: SearchUrlDao) {

    val allUrls: Flow<List<SearchUrl>> = searchUrlDao.getAllUrls()
    val allGroups: Flow<List<UrlGroup>> = searchUrlDao.getAllGroups()

    suspend fun insert(searchUrl: SearchUrl): Long {
        // The logic for orderIndex should be handled in ViewModel or before calling this
        return searchUrlDao.insert(searchUrl)
    }

    suspend fun update(searchUrl: SearchUrl) {
        searchUrlDao.update(searchUrl)
    }

    suspend fun updateAllUrls(searchUrls: List<SearchUrl>) {
        searchUrlDao.updateAll(searchUrls)
    }

    suspend fun delete(searchUrl: SearchUrl) {
        searchUrlDao.delete(searchUrl)
    }

    suspend fun insertGroup(group: UrlGroup): Long {
        return searchUrlDao.insertGroup(group)
    }

    suspend fun updateGroup(group: UrlGroup) {
        searchUrlDao.updateGroup(group)
    }

    suspend fun updateGroups(groups: List<UrlGroup>) {
        searchUrlDao.updateGroups(groups)
    }

    suspend fun deleteGroup(group: UrlGroup) {
        searchUrlDao.deleteGroup(group)
    }

    suspend fun getMaxOrderIndexForGroup(groupId: Long): Int? {
        return searchUrlDao.getMaxOrderIndexForGroup(groupId)
    }

    suspend fun getMaxGroupOrderIndex(): Int? {
        return searchUrlDao.getMaxGroupOrderIndex()
    }

    // 新增备份恢复功能所需方法
    suspend fun deleteAllGroups() {
        searchUrlDao.deleteAllGroups()
    }

    suspend fun deleteAllUrls() {
        searchUrlDao.deleteAllUrls()
    }

    suspend fun insertGroupWithId(group: UrlGroup): Long {
        return searchUrlDao.insertGroupWithId(group)
    }

    suspend fun insertUrlWithId(url: SearchUrl) {
        searchUrlDao.insertUrlWithId(url)
    }

    suspend fun initializeDatabaseIfFirstLaunch() {
        val groupCount = searchUrlDao.getGroupCount()
        if (groupCount == 0) {
            // Database is empty, populate it
            // 创建URL分组
            val urlGroup = UrlGroup(name = "搜索引擎", orderIndex = 0, color = "#FFFF00")
            val urlGroupId = searchUrlDao.insertGroup(urlGroup)

            // 添加URL搜索链接
            val baiduUrl = SearchUrl(name = "百度", urlPattern = "https://www.baidu.com/s?wd=%s", groupId = urlGroupId, orderIndex = 0, isEnabled = true, packageName = "")
            val bingUrl = SearchUrl(name = "Bing", urlPattern = "https://www.bing.com/search?q=%s", groupId = urlGroupId, orderIndex = 1, isEnabled = true, packageName = "")
            val googleUrl = SearchUrl(name = "Google", urlPattern = "https://www.google.com/search?q=%s", groupId = urlGroupId, orderIndex = 2, isEnabled = true, packageName = "")
            val yandexUrl = SearchUrl(name = "Yandex", urlPattern = "https://yandex.com/search/?text=%s", groupId = urlGroupId, orderIndex = 3, isEnabled = true, packageName = "")
            val bywebUrl = SearchUrl(name = "媒体搜索", urlPattern = "https://www.bing.com/search?q=%s site:zhihu.com | site:bilibili.com | site:douyin.com", groupId = urlGroupId, orderIndex = 4, isEnabled = true, packageName = "")
            val byweb2Url = SearchUrl(name = "开发社区", urlPattern = "https://www.bing.com/search?q=%s site:cnblogs.com | site:zhihu.com | site:csdn.net | site:juejin.cn | site:gitee.com", groupId = urlGroupId, orderIndex = 5, isEnabled = true, packageName = "")

            searchUrlDao.insert(baiduUrl)
            searchUrlDao.insert(bingUrl)
            searchUrlDao.insert(googleUrl)
            searchUrlDao.insert(yandexUrl)
            searchUrlDao.insert(bywebUrl)
            searchUrlDao.insert(byweb2Url)

            // 创建APP分组
            val appGroup = UrlGroup(name = "社交媒体", orderIndex = 1, color = "#2196F3")
            val appGroupId = searchUrlDao.insertGroup(appGroup)

            // 添加APP链接
            val zhihuUrl = SearchUrl(name = "知乎", urlPattern = "zhihu://search?q=%s", groupId = appGroupId, orderIndex = 0, isEnabled = true, packageName = "")
            val bilibiliUrl = SearchUrl(name = "哔哩哔哩", urlPattern = "bilibili://search/%s", groupId = appGroupId, orderIndex = 1, isEnabled = true, packageName = "")
            val xiaohongshuUrl = SearchUrl(name = "小红书", urlPattern = "xhsdiscover://search/result?keyword=%s", groupId = appGroupId, orderIndex = 2, isEnabled = true, packageName = "")
            val douyinUrl = SearchUrl(name = "抖音", urlPattern = "snssdk1128://search/trending?keyword=%s", groupId = appGroupId, orderIndex = 3, isEnabled = true, packageName = "")
            val weiboUrl = SearchUrl(name = "微博", urlPattern = "sinaweibo://searchall?q=%s", groupId = appGroupId, orderIndex = 4, isEnabled = true, packageName = "")
            val youtubeUrl = SearchUrl(name = "Youtube", urlPattern = "https://www.youtube.com/results?search_query=%s", groupId = appGroupId, orderIndex = 5, isEnabled = true, packageName = "")

            searchUrlDao.insert(zhihuUrl)
            searchUrlDao.insert(bilibiliUrl)
            searchUrlDao.insert(xiaohongshuUrl)
            searchUrlDao.insert(douyinUrl)
            searchUrlDao.insert(weiboUrl)
            searchUrlDao.insert(youtubeUrl)


            // 创建购物APP分组
            val shoppingGroup = UrlGroup(name = "购物", orderIndex = 2, color = "#FF0000")
            val shoppingGroupId = searchUrlDao.insertGroup(shoppingGroup)

            val pddUrl = SearchUrl(name = "拼多多", urlPattern = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=%s", groupId = shoppingGroupId, orderIndex = 0, isEnabled = true, packageName = "")
            val jdUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = shoppingGroupId, orderIndex = 1, isEnabled = true, packageName = "")
            val taobaoUrl = SearchUrl(name = "淘宝", urlPattern = "taobao://list.tmall.com/search_product.html?q=%s", groupId = shoppingGroupId, orderIndex = 2, isEnabled = true, packageName = "")
            val xianyuUrl = SearchUrl(name = "闲鱼", urlPattern = "fleamarket://searchitems?keyword=%s&searchType=0", groupId = shoppingGroupId, orderIndex = 3, isEnabled = true, packageName = "")

            searchUrlDao.insert(pddUrl)
            searchUrlDao.insert(jdUrl)
            searchUrlDao.insert(taobaoUrl)
            searchUrlDao.insert(xianyuUrl)

            // 创建应用商城分组
            val appStoreGroup = UrlGroup(name = "应用商城", orderIndex = 3, color = "#FFFF00")
            val appStoreGroupId = searchUrlDao.insertGroup(appStoreGroup)

            val vivoUrl = SearchUrl(name = "默认商店", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 0, isEnabled = true, packageName = "")
            val GooglePlayUrl = SearchUrl(name = "Google Play", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 1, isEnabled = true, packageName = "com.android.vending")


            searchUrlDao.insert(GooglePlayUrl)
            searchUrlDao.insert(vivoUrl)


            // 创建外卖APP分组
            val foodGroup = UrlGroup(name = "外卖", orderIndex = 4, color = "#00ff22ff")
            val foodGroupId = searchUrlDao.insertGroup(foodGroup)

            val meituanUrl = SearchUrl(name = "美团", urlPattern = "imeituan://www.meituan.com/search?q=%s", groupId = foodGroupId, orderIndex = 0, isEnabled = true, packageName = "")
            val elemeUrl = SearchUrl(name = "饿了么", urlPattern = "eleme://search?keyword=%s", groupId = foodGroupId, orderIndex = 1, isEnabled = true, packageName = "")
            val jdTakeoutUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = foodGroupId, orderIndex = 2, isEnabled = true, packageName = "")

            searchUrlDao.insert(meituanUrl)
            searchUrlDao.insert(elemeUrl)
            searchUrlDao.insert(jdTakeoutUrl)
        }
    }
}
