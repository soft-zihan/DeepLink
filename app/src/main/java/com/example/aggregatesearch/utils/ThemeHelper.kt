package com.example.aggregatesearch.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题帮助类，用于管理应用主题的切换功能
 */
class ThemeHelper(private val context: Context) {

    companion object {
        const val MODE_LIGHT = 0
        const val MODE_DARK = 1
        const val MODE_SYSTEM = 2

        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"

        // 初始化应用主题
        fun applyTheme(themeMode: Int) {
            when (themeMode) {
                MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                MODE_SYSTEM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                    }
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取当前主题模式
     * @return 主题模式：0=浅色，1=深色，2=跟随系统
     */
    fun getThemeMode(): Int {
        // 默认为系统模式
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /**
     * 设置主题模式
     * @param mode 主题模式：0=浅色，1=深色，2=跟随系统
     */
    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        applyTheme(mode)
    }
}
