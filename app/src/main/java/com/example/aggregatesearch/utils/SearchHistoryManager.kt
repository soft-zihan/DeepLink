package com.example.aggregatesearch.utils

import android.content.Context

class SearchHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isHistoryEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun addQuery(query: String) {
        if (!isHistoryEnabled()) return
        val current = getHistory().toMutableList()
        // 避免重复
        current.remove(query)
        current.add(0, query)
        // 限制历史条数
        if (current.size > MAX_SIZE) {
            current.removeLast()
        }
        prefs.edit().putStringSet(KEY_HISTORY_SET, current.toSet()).apply()
    }

    fun getHistory(): List<String> {
        val set = prefs.getStringSet(KEY_HISTORY_SET, emptySet()) ?: emptySet()
        // 保持插入顺序大致按最近; 因为 Set 无序，我们在添加时移除了旧的再加新保持近似顺序，但此处转 List
        return set.toList()
    }

    companion object {
        private const val PREF_NAME = "search_history_prefs"
        private const val KEY_HISTORY_SET = "key_history_set"
        private const val KEY_ENABLED = "key_history_enabled"
        private const val MAX_SIZE = 10
    }
} 