package com.example.aggregatesearch

import android.app.Application
import android.util.Log
import com.example.aggregatesearch.data.SearchUrlDatabase
import com.example.aggregatesearch.data.SearchUrlRepository
import com.example.aggregatesearch.utils.ThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SearchApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database: SearchUrlDatabase by lazy { SearchUrlDatabase.getDatabase(this) }
    val repository: SearchUrlRepository by lazy { SearchUrlRepository(database.searchUrlDao()) }

    override fun onCreate() {
        super.onCreate()
        try {
            // 初始化主题
            initializeTheme()

            // 强制初始化以捕获错误
            Log.i("SearchApplication", "Application onCreate: Initializing database and repository.")
            val repo = repository // 访问 repository 会触发 database 和 repository 的懒加载初始化
            Log.i("SearchApplication", "Application onCreate: Initialization successful.")
        } catch (e: Exception) {
            Log.e("SearchApplication", "CRITICAL: Error during application initialization!", e)
            // 抛出异常，确保应用崩溃并报告错误
            throw RuntimeException("Failed to initialize application", e)
        }
    }

    /**
     * 初始化应用主题设置
     */
    private fun initializeTheme() {
        val themeHelper = ThemeHelper(this)
        val currentThemeMode = themeHelper.getThemeMode()
        ThemeHelper.applyTheme(currentThemeMode)
        Log.i("SearchApplication", "Theme initialized: mode=$currentThemeMode")
    }
}
