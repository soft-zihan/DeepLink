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

@Database(entities = [SearchUrl::class, UrlGroup::class], version = 5, exportSchema = false)
abstract class SearchUrlDatabase : RoomDatabase() {

    abstract fun searchUrlDao(): SearchUrlDao

    companion object {
        @Volatile
        private var INSTANCE: SearchUrlDatabase? = null
        private const val TAG = "SearchUrlDatabase"

        fun getDatabase(context: Context): SearchUrlDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchUrlDatabase::class.java,
                    "search_url_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
