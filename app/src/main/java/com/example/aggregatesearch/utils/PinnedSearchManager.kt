package com.example.aggregatesearch.utils

import android.content.Context

class PinnedSearchManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun addPin(query: String) {
        val current = getPinned().toMutableList()
        // 避免重复
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_SIZE) {
            current.removeLast()
        }
        prefs.edit().putStringSet(KEY_PIN_SET, current.toSet()).apply()
    }

    fun removePin(query: String) {
        val current = getPinned().toMutableList()
        current.remove(query)
        prefs.edit().putStringSet(KEY_PIN_SET, current.toSet()).apply()
    }

    fun getPinned(): List<String> {
        val set = prefs.getStringSet(KEY_PIN_SET, emptySet()) ?: emptySet()
        return set.toList()
    }

    companion object {
        private const val PREF_NAME = "pin_search_prefs"
        private const val KEY_PIN_SET = "key_pin_set"
        private const val MAX_SIZE = 10
    }
} 