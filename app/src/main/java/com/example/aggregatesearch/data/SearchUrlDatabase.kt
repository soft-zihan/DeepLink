package com.example.aggregatesearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// Ensure MIGRATION_1_2 is imported if it's in the same package,
// otherwise, add the correct import statement e.g.:
// import com.example.aggregatesearch.data.MIGRATION_1_2

@Database(entities = [SearchUrl::class, UrlGroup::class], version = 2, exportSchema = false)
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
                                val urlGroup = UrlGroup(name = "url分组", orderIndex = 0)
                                val urlGroupId = searchUrlDao.insertGroup(urlGroup)

                                // 添加URL搜索链接
                                val baiduUrl = SearchUrl(name = "Baidu", urlPattern = "https://www.baidu.com/s?wd=%s", groupId = urlGroupId, orderIndex = 0, isEnabled = true)
                                val googleUrl = SearchUrl(name = "Google", urlPattern = "https://www.google.com/search?q=%s", groupId = urlGroupId, orderIndex = 1, isEnabled = true)
                                val bingUrl = SearchUrl(name = "Bing", urlPattern = "https://www.bing.com/search?q=%s", groupId = urlGroupId, orderIndex = 2, isEnabled = true)
                                val yandexUrl = SearchUrl(name = "Yandex", urlPattern = "https://yandex.com/search/?text=%s", groupId = urlGroupId, orderIndex = 3, isEnabled = true)

                                searchUrlDao.insert(baiduUrl)
                                searchUrlDao.insert(googleUrl)
                                searchUrlDao.insert(bingUrl)
                                searchUrlDao.insert(yandexUrl)

                                // 创建APP分组
                                val appGroup = UrlGroup(name = "app分组", orderIndex = 1)
                                val appGroupId = searchUrlDao.insertGroup(appGroup)

                                // 添加APP链接
                                val zhihuUrl = SearchUrl(name = "知乎", urlPattern = "zhihu://search?q=%s", groupId = appGroupId, orderIndex = 0, isEnabled = true)
                                val bilibiliUrl = SearchUrl(name = "哔哩哔哩", urlPattern = "bilibili://search/%s", groupId = appGroupId, orderIndex = 1, isEnabled = true)
                                val xiaohongshuUrl = SearchUrl(name = "小红书", urlPattern = "xhsdiscover://search/result?keyword=%s", groupId = appGroupId, orderIndex = 2, isEnabled = true)
                                val douyinUrl = SearchUrl(name = "抖音", urlPattern = "snssdk1128://search/trending?keyword=%s", groupId = appGroupId, orderIndex = 3, isEnabled = true)

                                searchUrlDao.insert(zhihuUrl)
                                searchUrlDao.insert(bilibiliUrl)
                                searchUrlDao.insert(xiaohongshuUrl)
                                searchUrlDao.insert(douyinUrl)
                            }
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2) // This line requires MIGRATION_1_2 to be accessible
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
