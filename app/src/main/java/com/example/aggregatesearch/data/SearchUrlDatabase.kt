package com.example.aggregatesearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

@Database(entities = [SearchUrl::class, UrlGroup::class], version = 2, exportSchema = false)
abstract class SearchUrlDatabase : RoomDatabase() {

    abstract fun searchUrlDao(): SearchUrlDao

    companion object {
        @Volatile
        private var INSTANCE: SearchUrlDatabase? = null
        private const val TAG = "SearchUrlDatabase"

        fun getDatabase(context: Context): SearchUrlDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        SearchUrlDatabase::class.java,
                        "search_url_database"
                    )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val searchUrlDao = database.searchUrlDao()

                                        // 创建URL分组
                                        val urlGroup = UrlGroup(name = "搜索引擎", orderIndex = 0, color = "#FFFF00")
                                        val urlGroupId = searchUrlDao.insertGroup(urlGroup)

                                        // 添加URL搜索链接
                                        val baiduUrl = SearchUrl(name = "Baidu", urlPattern = "https://www.baidu.com/s?wd=%s", groupId = urlGroupId, orderIndex = 0, isEnabled = true, packageName = "")
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
                                        val bilibiliUrl = SearchUrl(name = "哔哩哔哩", urlPattern = "bilibili://search/%s", groupId = appGroupId, orderIndex = 1, isEnabled = true, packageName = "tv.danmaku.bili")
                                        val xiaohongshuUrl = SearchUrl(name = "小红书", urlPattern = "xhsdiscover://search/result?keyword=%s", groupId = appGroupId, orderIndex = 2, isEnabled = true, packageName = "com.xingin.xhs")
                                        val douyinUrl = SearchUrl(name = "抖音", urlPattern = "snssdk1128://search/trending?keyword=%s", groupId = appGroupId, orderIndex = 3, isEnabled = true, packageName = "")
                                        val weiboUrl = SearchUrl(name = "微博", urlPattern = "sinaweibo://searchall?q=%s", groupId = appGroupId, orderIndex = 4, isEnabled = true, packageName = "")

                                        searchUrlDao.insert(zhihuUrl)
                                        searchUrlDao.insert(bilibiliUrl)
                                        searchUrlDao.insert(xiaohongshuUrl)
                                        searchUrlDao.insert(douyinUrl)
                                        searchUrlDao.insert(weiboUrl)


                                        // 创建购物APP分组
                                        val shoppingGroup = UrlGroup(name = "购物", orderIndex = 2, color = "#FF0000")
                                        val shoppingGroupId = searchUrlDao.insertGroup(shoppingGroup)

                                        val pddUrl = SearchUrl(name = "拼多多", urlPattern = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=%s", groupId = shoppingGroupId, orderIndex = 0, isEnabled = true, packageName = "com.xunmeng.pinduoduo")
                                        val jdUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = shoppingGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                        val taobaoUrl = SearchUrl(name = "淘宝", urlPattern = "taobao://list.tmall.com/search_product.html?q=%s", groupId = shoppingGroupId, orderIndex = 2, isEnabled = true, packageName = "")
                                        val xianyuUrl = SearchUrl(name = "闲鱼", urlPattern = "fleamarket://searchitems?keyword=%s&searchType=0", groupId = shoppingGroupId, orderIndex = 3, isEnabled = true, packageName = "")

                                        searchUrlDao.insert(pddUrl)
                                        searchUrlDao.insert(jdUrl)
                                        searchUrlDao.insert(taobaoUrl)
                                        searchUrlDao.insert(xianyuUrl)

                                        // 创建应用商城分组
                                        val appStoreGroup = UrlGroup(name = "应用商城", orderIndex = 3, color = "#FFFFFF")
                                        val appStoreGroupId = searchUrlDao.insertGroup(appStoreGroup)

                                        val vivoUrl = SearchUrl(name = "默认商店", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                        val GooglePlayUrl = SearchUrl(name = "Google Play", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 1, isEnabled = true, packageName = "com.android.vending")


                                        searchUrlDao.insert(GooglePlayUrl)
                                        searchUrlDao.insert(vivoUrl)


                                        // 创建外卖APP分组
                                        val foodGroup = UrlGroup(name = "外卖", orderIndex = 4, color = "#FFFFFF")
                                        val foodGroupId = searchUrlDao.insertGroup(foodGroup)

                                        val meituanUrl = SearchUrl(name = "美团", urlPattern = "imeituan://www.meituan.com/search?q=%s", groupId = foodGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                        val elemeUrl = SearchUrl(name = "饿了么", urlPattern = "eleme://search?keyword=%s", groupId = foodGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                        val jdTakeoutUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = foodGroupId, orderIndex = 2, isEnabled = true, packageName = "")

                                        searchUrlDao.insert(meituanUrl)
                                        searchUrlDao.insert(elemeUrl)
                                        searchUrlDao.insert(jdTakeoutUrl)


                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error populating database", e)
                                    }
                                }
                            }
                        }
                    })
                    .build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    Log.e(TAG, "数据库创建失败", e)
                    // 如果数据库创建失败，再次尝试创建
                    val fallbackInstance = Room.databaseBuilder(
                        context.applicationContext,
                        SearchUrlDatabase::class.java,
                        "search_url_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    INSTANCE = fallbackInstance
                    fallbackInstance
                }
            }
        }
    }
}
