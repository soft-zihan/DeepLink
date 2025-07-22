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
