/**
 * 搜索功能管理器
 * 负责处理搜索相关的所有功能
 * 包括：搜索执行、搜索历史管理、固定搜索词管理、搜索建议等
 */
package com.example.aggregatesearch.ui.search

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R
import com.example.aggregatesearch.SearchViewModel
import com.example.aggregatesearch.UrlLauncher
import com.example.aggregatesearch.adapters.PinnedSearchAdapter
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.databinding.ActivityMainBinding
import com.example.aggregatesearch.utils.PinnedSearchManager
import com.example.aggregatesearch.utils.SearchHistoryManager
import kotlinx.coroutines.launch

class SearchFunctionManager(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val searchViewModel: SearchViewModel,
    private val searchHistoryManager: SearchHistoryManager,
    private val pinnedSearchManager: PinnedSearchManager
) {

    private lateinit var pinnedAdapter: PinnedSearchAdapter
    private var isAllSelected: Boolean = false

    fun setupSearchFunction() {
        // 设置搜索按钮点击事件
        binding.buttonSearch.setOnClickListener {
            performSearch()
        }

        // 设置编辑框回车键检索
        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                return@setOnEditorActionListener true
            }
            false
        }

        // 点击搜索框时，如果已经有内容，则显示建议
        binding.editTextSearch.setOnTouchListener { v, _ ->
            (v as? AutoCompleteTextView)?.showDropDown()
            false
        }

        // 当搜索框获得焦点时显示历史记录
        binding.editTextSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                (binding.editTextSearch as? AutoCompleteTextView)?.showDropDown()
            }
        }
    }

    fun setupPinnedSearches() {
        binding.recyclerViewPinned.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        pinnedAdapter = PinnedSearchAdapter(
            pinnedWords = pinnedSearchManager.getPinned().toMutableList(),
            onWordClick = { word ->
                binding.editTextSearch.setText(word)
                performSearch()
            },
            onDeleteClick = { word ->
                pinnedSearchManager.removePin(word)
                pinnedAdapter.removeWord(word)
                Toast.makeText(context, "已删除: $word", Toast.LENGTH_SHORT).show()
                refreshPinnedSearches()
            }
        )
        binding.recyclerViewPinned.adapter = pinnedAdapter
        refreshPinnedSearches()
    }

    fun setupSelectAllButton(onStateChanged: (Boolean) -> Unit) {
        binding.buttonSelectAll.setOnClickListener {
            isAllSelected = !isAllSelected
            onStateChanged(isAllSelected)
            binding.buttonSelectAll.isActivated = isAllSelected
        }
    }

    fun updateSelectAllButtonState(allSelected: Boolean) {
        isAllSelected = allSelected
        binding.buttonSelectAll.isActivated = isAllSelected
    }

    fun performSearch() {
        val query = binding.editTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            // 添加到搜索历史
            searchHistoryManager.addQuery(query)

            // 获取所有勾选的链接
            val enabledUrls = searchViewModel.getEnabledUrls().filterIsInstance<SearchUrl>()

            if (enabledUrls.isNotEmpty()) {
                // 启动搜索
                UrlLauncher.launchSearchUrls(context, query, enabledUrls)
                refreshSearchHistorySuggestions() // 刷新搜索建议
            } else {
                Toast.makeText(context, "请先选择至少一个搜索引擎", Toast.LENGTH_SHORT).show()
            }

            // 同时向 DeepSeek 发送查询
            if (context is com.example.aggregatesearch.MainActivity) {
                (context as com.example.aggregatesearch.MainActivity).sendQueryToDeepSeek(query)
            }
        } else {
            Toast.makeText(context, "请输入搜索内容", Toast.LENGTH_SHORT).show()
        }
    }

    fun addPinnedSearch() {
        val query = binding.editTextSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            pinnedSearchManager.addPin(query)
            refreshPinnedSearches()
            Toast.makeText(context, "已固定", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "请输入内容后再固定", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshSearchHistorySuggestions() {
        // 获取搜索历史
        val histories = searchHistoryManager.getHistory()

        // 更新搜索框的自动完成建议
        if (binding.editTextSearch is AutoCompleteTextView) {
            val autoCompleteTextView = binding.editTextSearch as AutoCompleteTextView
            val adapter = ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                histories.toTypedArray()
            )
            autoCompleteTextView.setAdapter(adapter)
        }
    }

    fun refreshPinnedSearches() {
        val pinnedWords = pinnedSearchManager.getPinned()
        if (pinnedWords.isNotEmpty()) {
            binding.recyclerViewPinned.visibility = android.view.View.VISIBLE
            pinnedAdapter.setWords(pinnedWords)
        } else {
            binding.recyclerViewPinned.visibility = android.view.View.GONE
        }
    }

    fun getCurrentSearchQuery(): String {
        return binding.editTextSearch.text.toString().trim()
    }

    fun setPinnedEditMode(isEditMode: Boolean) {
        if (::pinnedAdapter.isInitialized) {
            pinnedAdapter.setEditMode(isEditMode)
        }
    }

    /**
     * 清空搜索框
     */
    fun clearSearchInput() {
        binding.editTextSearch.setText("")
    }

    /**
     * 设置搜索框内容
     */
    fun setSearchInput(text: String) {
        binding.editTextSearch.setText(text)
    }

    /**
     * 获取搜索历史数量
     */
    fun getSearchHistoryCount(): Int {
        return searchHistoryManager.getHistory().size
    }

    /**
     * 清空搜索历史
     */
    fun clearSearchHistory() {
        searchHistoryManager.clearHistory()
        refreshSearchHistorySuggestions()
        Toast.makeText(context, "搜索历史已清空", Toast.LENGTH_SHORT).show()
    }

    /**
     * 获取固定搜索词数量
     */
    fun getPinnedSearchCount(): Int {
        return pinnedSearchManager.getPinned().size
    }

    /**
     * 清空所有固定搜索词
     */
    fun clearAllPinnedSearches() {
        pinnedSearchManager.clearAll()
        refreshPinnedSearches()
        Toast.makeText(context, "固定搜索词已清空", Toast.LENGTH_SHORT).show()
    }
}
