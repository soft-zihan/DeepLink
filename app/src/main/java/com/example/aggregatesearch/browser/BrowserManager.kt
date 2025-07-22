package com.example.aggregatesearch.browser

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class BrowserManager(private val context: Context) {

    private val _tabs = MutableLiveData<MutableList<BrowserTab>>(mutableListOf())
    val tabs: LiveData<MutableList<BrowserTab>> = _tabs

    private val _currentTab = MutableLiveData<BrowserTab>()
    val currentTab: LiveData<BrowserTab> = _currentTab

    private val webViews = mutableMapOf<String, WebView>()

    init {
        // 初始化时添加主标签
        val mainTab = BrowserTab.createMainTab()
        _tabs.value?.add(mainTab)
        _currentTab.value = mainTab
    }

    fun addWebTab(url: String): BrowserTab {
        val newTab = BrowserTab.createWebTab(url)
        _tabs.value?.add(newTab)
        _tabs.postValue(_tabs.value)

        // 创建对应的WebView
        createWebView(newTab)

        return newTab
    }

    private fun createWebView(tab: BrowserTab): WebView {
        val webView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 更新标签标题
                    view?.title?.let { title ->
                        updateTabTitle(tab.id, title)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let {
                        updateTabTitle(tab.id, it)
                    }
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }

        webViews[tab.id] = webView
        webView.loadUrl(tab.url)

        return webView
    }

    private fun updateTabTitle(tabId: String, title: String) {
        _tabs.value?.let { tabList ->
            val tabIndex = tabList.indexOfFirst { it.id == tabId }
            if (tabIndex != -1) {
                val updatedTab = tabList[tabIndex].copy(title = title)
                tabList[tabIndex] = updatedTab
                _tabs.postValue(tabList)

                // 如果是当前标签，也更新当前标签
                if (_currentTab.value?.id == tabId) {
                    _currentTab.postValue(updatedTab)
                }
            }
        }
    }

    fun switchToTab(tab: BrowserTab) {
        _currentTab.postValue(tab)
    }

    fun removeTab(tab: BrowserTab) {
        if (tab.isMainTab) return // 主标签不能删除

        _tabs.value?.let { tabList ->
            tabList.remove(tab)
            _tabs.postValue(tabList)

            // 清理WebView
            webViews[tab.id]?.destroy()
            webViews.remove(tab.id)

            // 如果删除的是当前标签，切换到主标签
            if (_currentTab.value?.id == tab.id) {
                val mainTab = tabList.firstOrNull { it.isMainTab }
                mainTab?.let { switchToTab(it) }
            }
        }
    }

    fun getWebView(tabId: String): WebView? {
        return webViews[tabId]
    }

    fun hasWebTabs(): Boolean {
        return _tabs.value?.any { !it.isMainTab } == true
    }

    fun canGoBack(): Boolean {
        val currentTabId = _currentTab.value?.id
        return if (currentTabId != null && !_currentTab.value!!.isMainTab) {
            webViews[currentTabId]?.canGoBack() == true
        } else {
            false
        }
    }

    fun goBack() {
        val currentTabId = _currentTab.value?.id
        if (currentTabId != null && !_currentTab.value!!.isMainTab) {
            webViews[currentTabId]?.goBack()
        }
    }

    fun getCurrentWebView(): WebView? {
        val currentTabId = _currentTab.value?.id
        return if (currentTabId != null) webViews[currentTabId] else null
    }
}
