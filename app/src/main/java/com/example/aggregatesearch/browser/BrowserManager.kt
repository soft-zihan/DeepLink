package com.example.aggregatesearch.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import java.net.URL
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.aggregatesearch.SearchViewModel
import com.example.aggregatesearch.data.SearchUrl

class BrowserManager(
    private val context: Context,
    private val searchViewModel: SearchViewModel
) {

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

    fun addWebTab(searchUrl: SearchUrl): BrowserTab {
        val newTab = BrowserTab.createWebTab(searchUrl.urlPattern, searchUrl.name)
        _tabs.value?.add(newTab)
        _tabs.postValue(_tabs.value)

        // 创建对应的WebView
        createWebView(newTab, searchUrl)

        return newTab
    }

    private fun createWebView(tab: BrowserTab, searchUrl: SearchUrl): WebView {
        val webView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        return false // 内部加载
                    }
                    // 拦截其他所有协议的跳转
                    android.widget.Toast.makeText(context, "已拦截跳转: $url", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 更新标签标题
                    view?.title?.let { title ->
                        updateTabTitle(tab.id, title)
                    }
                    // 自动保存Cookie
                    url?.let {
                        val cookies = CookieManager.getInstance().getCookie(it)
                        if (cookies != null && cookies != searchUrl.autoCookie) {
                            searchViewModel.updateAutoCookieForUrl(searchUrl.id, cookies)
                        }
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
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = when (searchUrl.userAgent) {
                    "desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    else -> WebView(context).settings.userAgentString
                }
            }
        }

        // 设置Cookie
        val cookieToUse = if (searchUrl.cookie.isNotEmpty()) {
            searchUrl.cookie
        } else {
            searchUrl.autoCookie
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        if (cookieToUse.isNotEmpty()) {
            val baseUrl = try {
                val url = URL(searchUrl.urlPattern)
                "${url.protocol}://${url.host}"
            } catch (e: Exception) {
                Log.e("BrowserManager", "Invalid URL, using original pattern for cookie domain.", e)
                searchUrl.urlPattern
            }
            val cookies = cookieToUse.split(';')
            for (cookie in cookies) {
                if (cookie.contains("=")) {
                    val trimmedCookie = cookie.trim()
                    cookieManager.setCookie(baseUrl, trimmedCookie)
                    Log.d("BrowserManager", "Set cookie: $trimmedCookie for $baseUrl")
                }
            }
            cookieManager.flush()
            Log.d("BrowserManager", "Cookies flushed for domain: $baseUrl")
        }

        // 在所有 cookie 操作完成后再加载 URL
        webView.loadUrl(tab.url)

        webViews[tab.id] = webView
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

    fun hasMultipleWebTabs(): Boolean {
        return _tabs.value?.count { !it.isMainTab } ?: 0 > 1
    }

    fun closeNonPrimaryTabs() {
        _tabs.value?.let { tabList ->
            val tabsToRemove = tabList.filter { !it.isMainTab }
            tabsToRemove.forEach { tab ->
                webViews[tab.id]?.destroy()
                webViews.remove(tab.id)
            }
            val mainTab = tabList.firstOrNull { it.isMainTab }
            _tabs.postValue(mutableListOf(mainTab!!))
            switchToTab(mainTab)
        }
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
