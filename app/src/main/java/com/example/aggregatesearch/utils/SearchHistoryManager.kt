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
        while (current.size > MAX_SIZE) {
            current.removeLast()
        }
        prefs.edit().putString(KEY_HISTORY_LIST, current.joinToString(SEPARATOR)).apply()
    }

    fun getHistory(): List<String> {
        val historyString = prefs.getString(KEY_HISTORY_LIST, null) ?: return emptyList()
        return historyString.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY_LIST).apply()
    }

    companion object {
        private const val PREF_NAME = "search_history_prefs"
        private const val KEY_HISTORY_LIST = "key_history_list"
        private const val KEY_ENABLED = "key_history_enabled"
        private const val MAX_SIZE = 20 // 增加历史记录容量
        private const val SEPARATOR = "‚‗‚"
    }
}
