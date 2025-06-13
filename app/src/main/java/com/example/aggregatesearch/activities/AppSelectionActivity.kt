package com.example.aggregatesearch.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aggregatesearch.R
import com.example.aggregatesearch.adapters.AppAdapter
import com.example.aggregatesearch.databinding.ActivityAppSelectionBinding
import com.example.aggregatesearch.utils.AppPackageManager
import com.example.aggregatesearch.utils.UiUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppSelectionViewModel(private val appPackageManager: AppPackageManager) : ViewModel() {
    private val _apps = MutableStateFlow<List<AppPackageManager.AppInfo>>(emptyList())
    val apps: StateFlow<List<AppPackageManager.AppInfo>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 缓存所有应用列表
    private var allApps: List<AppPackageManager.AppInfo> = emptyList()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            allApps = appPackageManager.getInstalledApps()
            _apps.value = allApps
            _isLoading.value = false
        }
    }

    fun searchApps(query: String) {
        viewModelScope.launch {
            _isLoading.value = true

            if (query.isEmpty()) {
                _apps.value = allApps
            } else {
                // 增强搜索能力：分词搜索、拼音匹配、多关键词匹配
                val lowerCaseQuery = query.lowercase()
                val keywords = lowerCaseQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }

                val results = allApps.filter { app ->
                    val nameLower = app.appName.lowercase()
                    val packageLower = app.packageName.lowercase()

                    // 匹配任何关键词
                    keywords.any { keyword ->
                        nameLower.contains(keyword) || packageLower.contains(keyword) ||
                        // 针对常见应用的特殊处理
                        (keyword == "微信" && (packageLower.contains("wechat") || packageLower.contains("weixin"))) ||
                        (keyword == "支付宝" && packageLower.contains("alipay")) ||
                        (keyword == "qq" && (packageLower.contains("mobileqq") || packageLower == "com.tencent.qq"))
                    }
                }.sortedBy { it.appName }

                _apps.value = results
            }

            _isLoading.value = false
        }
    }
}

class AppSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppSelectionBinding
    private lateinit var appAdapter: AppAdapter

    private val viewModel: AppSelectionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppSelectionViewModel(AppPackageManager(applicationContext)) as T
            }
        }
    }

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UiUtils.applyToolbarColor(this)

        setupRecyclerView()
        setupSearchInput()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.btnClearBind.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_PACKAGE_NAME, "")
                putExtra(EXTRA_APP_NAME, "")
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        val colorStr = getSharedPreferences("ui_prefs", 0).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val tint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorStr))
        binding.btnClearBind.backgroundTintList = tint
        binding.btnClearBind.setTextColor(android.graphics.Color.WHITE)
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appInfo ->
            val resultIntent = Intent().apply {
                putExtra(EXTRA_PACKAGE_NAME, appInfo.packageName)
                putExtra(EXTRA_APP_NAME, appInfo.appName)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(this@AppSelectionActivity)
            adapter = appAdapter
        }
    }

    private fun setupSearchInput() {
        binding.editTextSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = viewModel.viewModelScope.launch {
                    delay(300) // 防抖
                    viewModel.searchApps(s?.toString() ?: "")
                }
            }
        })
    }

    private fun observeViewModel() {
        // 观察应用列表
        viewModel.viewModelScope.launch {
            viewModel.apps.collect { apps ->
                appAdapter.submitList(apps)
                binding.emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // 观察加载状态
        viewModel.viewModelScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UiUtils.applyToolbarColor(this)
        val colorStr = getSharedPreferences("ui_prefs", 0).getString("toolbar_color", "#6200EE") ?: "#6200EE"
        val tint2 = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorStr))
        binding.btnClearBind.backgroundTintList = tint2
        binding.btnClearBind.setTextColor(android.graphics.Color.WHITE)
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val REQUEST_CODE_SELECT_APP = 101
    }
}
