package com.example.aggregatesearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SearchUrl::class, UrlGroup::class], version = 1, exportSchema = false)
abstract class SearchUrlDatabase : RoomDatabase() {

    abstract fun searchUrlDao(): SearchUrlDao

    companion object {
        @Volatile
        private var INSTANCE: SearchUrlDatabase? = null

        fun getDatabase(context: Context): SearchUrlDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchUrlDatabase::class.java,
                    "search_url_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val searchUrlDao = database.searchUrlDao()

                                // 创建URL分组
                                val urlGroup = UrlGroup(name = "搜索引擎", orderIndex = 0)
                                val urlGroupId = searchUrlDao.insertGroup(urlGroup)

                                // 添加URL搜索链接
                                val baiduUrl = SearchUrl(name = "Baidu", urlPattern = "https://www.baidu.com/s?wd=%s", groupId = urlGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val bingUrl = SearchUrl(name = "Bing", urlPattern = "https://www.bing.com/search?q=%s", groupId = urlGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                val googleUrl = SearchUrl(name = "Google", urlPattern = "https://www.google.com/search?q=%s", groupId = urlGroupId, orderIndex = 2, isEnabled = true, packageName = "")
                                val yandexUrl = SearchUrl(name = "Yandex", urlPattern = "https://yandex.com/search/?text=%s", groupId = urlGroupId, orderIndex = 3, isEnabled = true, packageName = "")

                                searchUrlDao.insert(baiduUrl)
                                searchUrlDao.insert(bingUrl)
                                searchUrlDao.insert(googleUrl)
                                searchUrlDao.insert(yandexUrl)

                                // 创建APP分组
                                val appGroup = UrlGroup(name = "社交媒体", orderIndex = 1)
                                val appGroupId = searchUrlDao.insertGroup(appGroup)

                                // 添加APP链接(贴吧、公众号暂未成功)
                                val zhihuUrl = SearchUrl(name = "知乎", urlPattern = "zhihu://search?q=%s", groupId = appGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val bilibiliUrl = SearchUrl(name = "哔哩哔哩", urlPattern = "bilibili://search/%s", groupId = appGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                val xiaohongshuUrl = SearchUrl(name = "小红书", urlPattern = "xhsdiscover://search/result?keyword=%s", groupId = appGroupId, orderIndex = 2, isEnabled = true, packageName = "")
                                val douyinUrl = SearchUrl(name = "抖音", urlPattern = "snssdk1128://search/trending?keyword=%s", groupId = appGroupId, orderIndex = 3, isEnabled = true, packageName = "")
                                /*val wechatUrl = SearchUrl(name = "公众号", urlPattern = "weixin://dl/officialaccounts?search=%s", groupId = appGroupId, orderIndex = 4, isEnabled = true, packageName = "")
                                val tiebaUrl = SearchUrl(name = "贴吧", urlPattern = "tieba://search?keyword=%s", groupId = appGroupId, orderIndex = 6, isEnabled = true, packageName = "")*/
                                val weiboUrl = SearchUrl(name = "微博", urlPattern = "sinaweibo://searchall?q=%s", groupId = appGroupId, orderIndex = 5, isEnabled = true, packageName = "")

                                searchUrlDao.insert(zhihuUrl)
                                searchUrlDao.insert(bilibiliUrl)
                                searchUrlDao.insert(xiaohongshuUrl)
                                searchUrlDao.insert(douyinUrl)
/*                                searchUrlDao.insert(wechatUrl)
                                searchUrlDao.insert(tiebaUrl)*/
                                searchUrlDao.insert(weiboUrl)

                                // 创建应用商城分组
                                val appStoreGroup = UrlGroup(name = "应用商城", orderIndex = 2)
                                val appStoreGroupId = searchUrlDao.insertGroup(appStoreGroup)

                                val vivoUrl = SearchUrl(name = "默认商店", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val GooglePlayUrl = SearchUrl(name = "Google Play", urlPattern = "market://search?q=%s", groupId = appStoreGroupId, orderIndex = 1, isEnabled = true, packageName = "com.android.vending")
                                val coolUrl = SearchUrl(name = "酷安", urlPattern = "coolmarket://com.coolapk.market/search", groupId = appStoreGroupId, orderIndex = 2, isEnabled = true, packageName = "")


                                searchUrlDao.insert(GooglePlayUrl)
                                searchUrlDao.insert(vivoUrl)
                                searchUrlDao.insert(coolUrl)


                                // 创建购物APP分组（淘宝暂未成功）
                                val shoppingGroup = UrlGroup(name = "购物", orderIndex = 3)
                                val shoppingGroupId = searchUrlDao.insertGroup(shoppingGroup)

                                val pddUrl = SearchUrl(name = "拼多多", urlPattern = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=%s", groupId = shoppingGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val jdUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = shoppingGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                               /* val taobaoUrl = SearchUrl(name = "淘宝", urlPattern = "尚未成功获得", groupId = shoppingGroupId, orderIndex = 2, isEnabled = true, packageName = "")*/
                                val xianyuUrl = SearchUrl(name = "闲鱼", urlPattern = "fleamarket://searchitems?keyword=%s&searchType=0", groupId = shoppingGroupId, orderIndex = 3, isEnabled = true, packageName = "")

                                searchUrlDao.insert(pddUrl)
                                searchUrlDao.insert(jdUrl)
                           /*     searchUrlDao.insert(taobaoUrl)*/
                                searchUrlDao.insert(xianyuUrl)


                                // 创建外卖APP分组
                                val foodGroup = UrlGroup(name = "外卖", orderIndex = 4)
                                val foodGroupId = searchUrlDao.insertGroup(foodGroup)

                                val meituanUrl = SearchUrl(name = "美团", urlPattern = "imeituan://www.meituan.com/search?q=%s", groupId = foodGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val elemeUrl = SearchUrl(name = "饿了么", urlPattern = "eleme://search?keyword=%s", groupId = foodGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                val jdTakeoutUrl = SearchUrl(name = "京东", urlPattern = "openApp.jdMobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"%s\"}", groupId = foodGroupId, orderIndex = 2, isEnabled = true, packageName = "")

                                searchUrlDao.insert(meituanUrl)
                                searchUrlDao.insert(elemeUrl)
                                searchUrlDao.insert(jdTakeoutUrl)


/*                                // 创建开发者资源APP分组
                                val devGroup = UrlGroup(name = "开发者资源", orderIndex = 6)
                                val devGroupId = searchUrlDao.insertGroup(devGroup)

                                // 添加开发者资源链接
                                val githubUrl = SearchUrl(name = "GitHub", urlPattern = "https://github.com/search?q=%s", groupId = devGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val csdnUrl = SearchUrl(name = "CSDN", urlPattern = "https://so.csdn.net/so/search?q=%s", groupId = devGroupId, orderIndex = 1, isEnabled = true, packageName = "")
                                val juejinUrl = SearchUrl(name = "掘金", urlPattern = "https://juejin.cn/search?query=%s", groupId = devGroupId, orderIndex = 2, isEnabled = true, packageName = "")
                                val stackOverflowUrl = SearchUrl(name = "Stack Overflow", urlPattern = "https://stackoverflow.com/search?q=%s", groupId = devGroupId, orderIndex = 3, isEnabled = true, packageName = "")
                                val cnblogsUrl = SearchUrl(name = "博客园", urlPattern = "https://www.cnblogs.com/search/default.aspx?q=%s",groupId = devGroupId, orderIndex = 4, isEnabled = true, packageName = "")

                                searchUrlDao.insert(githubUrl)
                                searchUrlDao.insert(csdnUrl)
                                searchUrlDao.insert(juejinUrl)
                                searchUrlDao.insert(stackOverflowUrl)
                                searchUrlDao.insert(cnblogsUrl)


                                // 创建墙外APP分组
                                val wallGroup = UrlGroup(name = "墙外资源", orderIndex = 8)
                                val wallGroupId = searchUrlDao.insertGroup(wallGroup)

                                val youtubeUrl = SearchUrl(name = "YouTube", urlPattern = "https://www.youtube.com/results?search_query=%s", groupId = wallGroupId, orderIndex = 0, isEnabled = true, packageName = "")
                                val twitterUrl = SearchUrl(name = "Twitter", urlPattern = "https://twitter.com/search?q=%s", groupId = wallGroupId, orderIndex = 1, isEnabled = true, packageName = "")

                                searchUrlDao.insert(youtubeUrl)
                                searchUrlDao.insert(twitterUrl)*/
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
