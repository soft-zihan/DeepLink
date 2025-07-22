package com.example.aggregatesearch.utils

import android.content.Context
import android.text.TextUtils

class PinnedSearchManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun addPin(query: String) {
        val current = getPinned().toMutableList()
        // 避免重复
        current.remove(query)
        // 添加到最前面，实现按时间排序（新的在前）
        current.add(0, query)
        if (current.size > MAX_SIZE) {
            current.removeLast()
        }
        // 使用分隔符保存为字符串
        prefs.edit().putString(KEY_PIN_LIST, TextUtils.join(SEPARATOR, current)).apply()
    }

    fun removePin(query: String) {
        val current = getPinned().toMutableList()
        current.remove(query)
        prefs.edit().putString(KEY_PIN_LIST, TextUtils.join(SEPARATOR, current)).apply()
    }

    fun getPinned(): List<String> {
        // 迁移旧数据
        if (prefs.contains(KEY_PIN_SET_OLD)) {
            val oldSet = prefs.getStringSet(KEY_PIN_SET_OLD, emptySet()) ?: emptySet()
            if (oldSet.isNotEmpty()) {
                val oldList = oldSet.toList()
                prefs.edit()
                    .putString(KEY_PIN_LIST, TextUtils.join(SEPARATOR, oldList))
                    .remove(KEY_PIN_SET_OLD)
                    .apply()
                return oldList
            } else {
                // 如果旧数据为空，也直接移除旧的key
                prefs.edit().remove(KEY_PIN_SET_OLD).apply()
            }
        }

        val savedString = prefs.getString(KEY_PIN_LIST, null)
        return if (savedString.isNullOrEmpty()) {
            emptyList()
        } else {
            savedString.split(SEPARATOR)
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_PIN_LIST).apply()
    }

    companion object {
        private const val PREF_NAME = "pin_search_prefs"
        private const val KEY_PIN_SET_OLD = "key_pin_set" // 旧的Key
        private const val KEY_PIN_LIST = "key_pin_list_ordered" // 新的Key
        private const val MAX_SIZE = 10
        private const val SEPARATOR = "‚‗‚" // 一个不太可能出现在搜索词中的分隔符
    }
}
