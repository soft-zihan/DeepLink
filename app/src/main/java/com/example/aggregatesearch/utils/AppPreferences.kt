package com.example.aggregatesearch.utils

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME = "key_theme"
        const val KEY_USE_BUILT_IN_BROWSER = "key_use_built_in_browser"
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(): String? {
        return prefs.getString(KEY_THEME, null)
    }

    fun setUseBuiltInBrowser(useBuiltIn: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BUILT_IN_BROWSER, useBuiltIn).apply()
    }

    fun getUseBuiltInBrowser(): Boolean {
        return prefs.getBoolean(KEY_USE_BUILT_IN_BROWSER, true) // 默认使用内置浏览器
    }
}
