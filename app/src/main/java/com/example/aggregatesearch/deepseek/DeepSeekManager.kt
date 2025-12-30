package com.example.aggregatesearch.deepseek

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.edit

/**
 * DeepSeek WebView 管理器
 * 负责管理 DeepSeek 聊天的 WebView，包括：
 * - Cookie 管理和持久化
 * - 展开/折叠状态管理
 * - 搜索查询发送
 */
class DeepSeekManager(
    private val context: Context,
    private val container: LinearLayout,
    private val webViewContainer: FrameLayout,
    private val headerView: LinearLayout,
    private val titleText: TextView,
    private val expandIcon: ImageView,
    private val progressBar: ProgressBar,
    private val statusText: TextView? = null
) {
    companion object {
        private const val TAG = "DeepSeekManager"
        private const val PREFS_NAME = "deepseek_prefs"
        private const val KEY_COOKIE = "deepseek_cookie"
        private const val KEY_EXPANDED = "deepseek_expanded"
        private const val KEY_ENABLED = "deepseek_enabled"
        
        const val DEEPSEEK_URL = "https://chat.deepseek.com/"
        const val DEEPSEEK_NEW_CHAT_URL = "https://chat.deepseek.com/a/chat/s/new"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var webView: WebView? = null
    private var isExpanded: Boolean = prefs.getBoolean(KEY_EXPANDED, true)
    private var isEnabled: Boolean = prefs.getBoolean(KEY_ENABLED, true)
    private var pendingQuery: String? = null
    private var isPageLoaded: Boolean = false
    private var isInitialized: Boolean = false
    private var isLoggedIn: Boolean = false
    
    init {
        setupHeader()
        updateExpandState()
    }
    
    /**
     * 初始化 WebView（延迟初始化，首次需要时才创建）
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        
        if (!isEnabled) {
            container.visibility = View.GONE
            return
        }
        
        container.visibility = View.VISIBLE
        createWebView()
    }
    
    private fun createWebView() {
        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // 确保 WebView 可获得焦点并可滚动
            isFocusable = true
            isFocusableInTouchMode = true
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            isNestedScrollingEnabled = true

            // 关键：处理滑动事件
            // 这里强制让 WebView 优先处理滚动，避免 DeepSeek 页面“完全不能滑动”。
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
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
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 允许文件访问
                allowFileAccess = true
                allowContentAccess = true
                // 缓存设置
                cacheMode = WebSettings.LOAD_DEFAULT
                // 设置 User Agent 为移动端
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
        
        // 设置 Cookie
        setupCookies()
        
        // 添加到容器
        webViewContainer.addView(webView)
        
        // 加载页面
        webView?.loadUrl(DEEPSEEK_URL)
    }
    
    private fun setupCookies() {
        val cookie = getCookie()
        if (cookie.isNotEmpty()) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            webView?.let { cookieManager.setAcceptThirdPartyCookies(it, true) }
            
            // 设置 Cookie
            val cookies = cookie.split(";")
            for (c in cookies) {
                if (c.contains("=")) {
                    val trimmedCookie = c.trim()
                    cookieManager.setCookie(DEEPSEEK_URL, trimmedCookie)
                    Log.d(TAG, "Set cookie: $trimmedCookie")
                }
            }
            cookieManager.flush()
        }
    }
    
    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // 只允许 DeepSeek 相关的 URL
                return if (url.contains("deepseek.com")) {
                    false // 内部加载
                } else {
                    Log.d(TAG, "Blocked external URL: $url")
                    true // 拦截外部链接
                }
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = false
                progressBar.visibility = View.VISIBLE
                updateStatusText("加载中...")
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                isPageLoaded = true
                
                // 自动保存 Cookie
                url?.let { saveCookieFromPage(it) }
                
                // 检测登录状态（不做 CSS 注入；移动端页面本身无侧边栏，注入反而可能影响滚动）
                checkLoginStatusAndInject(url)
                
                // 如果有待处理的查询，执行搜索
                pendingQuery?.let { query ->
                    pendingQuery = null
                    executeSearch(query)
                }
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                progressBar.visibility = View.GONE
                updateStatusText("加载失败")
                Log.e(TAG, "WebView error: ${error?.description}")
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // 只用一个细进度条提示加载状态；避免覆盖 WebView 导致手势问题
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                progressBar.progress = newProgress
            }
        }
    }

    private fun updateStatusText(text: String?) {
        val statusView = statusText ?: return
        if (text.isNullOrBlank()) {
            statusView.text = ""
            statusView.visibility = View.GONE
        } else {
            statusView.text = text
            statusView.visibility = View.VISIBLE
        }
    }
    
    /**
     * 检测登录状态并注入适当的脚本
     */
    private fun checkLoginStatusAndInject(url: String?) {
        // 仅检测登录状态：移动端页面无需（也不应）注入隐藏侧边栏的 CSS/JS
        webView?.evaluateJavascript(
            """
            (function() {
                var loginForm = document.querySelector('form[class*="login"], form[class*="sign"], [class*="login-form"], [class*="auth"]');
                var chatInput = document.querySelector('textarea, [class*="chat-input"], [contenteditable="true"]');

                if (chatInput && !loginForm) {
                    return 'logged_in';
                }
                return 'not_logged_in';
            })();
            """.trimIndent()
        ) { result ->
            val status = result?.replace("\"", "") ?: "unknown"
            isLoggedIn = status == "logged_in"

            if (isLoggedIn) {
                updateStatusText(null)
            } else {
                val isLoginPage = url?.contains("/sign_in") == true ||
                    url?.contains("/login") == true ||
                    url == DEEPSEEK_URL
                if (isLoginPage) {
                    updateStatusText("请登录")
                }
            }
        }
    }
    
    /**
     * 从页面保存 Cookie
     */
    private fun saveCookieFromPage(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        if (!cookies.isNullOrEmpty()) {
            val currentCookie = getCookie()
            if (cookies != currentCookie) {
                saveCookie(cookies)
                Log.d(TAG, "Auto-saved cookies from page")
            }
        }
    }
    
    /**
     * 设置头部点击事件
     */
    private fun setupHeader() {
        headerView.setOnClickListener {
            toggleExpand()
        }
    }
    
    /**
     * 切换展开/折叠状态
     */
    fun toggleExpand() {
        isExpanded = !isExpanded
        prefs.edit { putBoolean(KEY_EXPANDED, isExpanded) }
        updateExpandState()
    }
    
    /**
     * 更新展开/折叠状态的 UI
     */
    private fun updateExpandState() {
        if (isExpanded) {
            webViewContainer.visibility = View.VISIBLE
            expandIcon.rotation = 180f // 向上箭头
        } else {
            webViewContainer.visibility = View.GONE
            expandIcon.rotation = 0f // 向下箭头
        }
    }
    
    /**
     * 发送搜索查询到 DeepSeek
     * @param query 搜索查询内容
     */
    fun sendQuery(query: String) {
        if (!isEnabled || query.isEmpty()) return
        
        // 确保已初始化
        if (!isInitialized) {
            initialize()
        }
        
        if (!isPageLoaded) {
            // 页面还未加载完成，保存待处理的查询
            pendingQuery = query
            // 加载新对话页面
            webView?.loadUrl(DEEPSEEK_NEW_CHAT_URL)
            return
        }
        
        // 先导航到新对话页面
        webView?.loadUrl(DEEPSEEK_NEW_CHAT_URL)
        pendingQuery = query
    }
    
    /**
     * 执行搜索（在页面加载完成后调用）
     */
    private fun executeSearch(query: String) {
        // 等待一小段时间让页面完全渲染
        webView?.postDelayed({
            val escapedQuery = query
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\"", "\\\"")
            
            val searchScript = """
                (function() {
                    var query = '$escapedQuery';
                    var retryCount = 0;
                    var maxRetries = 15;
                    
                    function log(msg) {
                        console.log('[DeepSeek AutoSearch] ' + msg);
                    }
                    
                    // 查找并填充输入框
                    function findAndFillInput() {
                        log('尝试查找输入框, 重试次数: ' + retryCount);
                        
                        // DeepSeek 聊天输入框 - 优先使用 id
                        var input = document.getElementById('chat-input');
                        
                        if (!input) {
                            // 备选选择器
                            var selectors = [
                                'textarea[placeholder]',
                                'textarea',
                                'div[contenteditable="true"]',
                                '[role="textbox"]'
                            ];
                            
                            for (var i = 0; i < selectors.length; i++) {
                                var elements = document.querySelectorAll(selectors[i]);
                                for (var j = 0; j < elements.length; j++) {
                                    var el = elements[j];
                                    var rect = el.getBoundingClientRect();
                                    if (rect.width > 0 && rect.height > 0) {
                                        input = el;
                                        log('找到输入框: ' + selectors[i]);
                                        break;
                                    }
                                }
                                if (input) break;
                            }
                        } else {
                            log('找到输入框: #chat-input');
                        }
                        
                        if (!input) {
                            if (retryCount < maxRetries) {
                                retryCount++;
                                setTimeout(findAndFillInput, 500);
                            } else {
                                log('未能找到输入框');
                            }
                            return;
                        }
                        
                        // 聚焦输入框
                        input.focus();
                        input.click();
                        
                        // 填充内容 - 使用多种方式确保 React 能检测到变化
                        if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                            // 清空现有内容
                            input.value = '';
                            
                            // 使用 native setter 绕过 React 的值追踪
                            var descriptor = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
                            if (descriptor && descriptor.set) {
                                descriptor.set.call(input, query);
                            } else {
                                input.value = query;
                            }
                            
                            // 触发完整的事件序列
                            input.dispatchEvent(new Event('focus', { bubbles: true }));
                            input.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                            input.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                            
                            // 额外触发 compositionend 事件（对于中文输入法）
                            input.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: query }));
                        } else {
                            // contenteditable 元素
                            input.textContent = query;
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        
                        log('已填充内容: ' + query);

                        // 先确保选择“联网搜索”，并避免误开“深度思考”
                        setTimeout(function() {
                            ensureWebSearchMode();
                            setTimeout(submitSearch, 600);
                        }, 600);
                    }

                    function isActive(el) {
                        if (!el) return false;
                        var ariaPressed = el.getAttribute('aria-pressed');
                        var ariaSelected = el.getAttribute('aria-selected');
                        var ariaChecked = el.getAttribute('aria-checked');
                        var ariaCurrent = el.getAttribute('aria-current');
                        var cls = (el.className || '').toString();
                        return ariaPressed === 'true' || ariaSelected === 'true' || ariaChecked === 'true' ||
                            ariaCurrent === 'true' || /active|selected|checked|on/i.test(cls);
                    }

                    function clickIfNeededByText(textIncludes, shouldBeActive) {
                        var nodes = document.querySelectorAll('button, [role="button"], [tabindex]');
                        for (var i = 0; i < nodes.length; i++) {
                            var el = nodes[i];
                            var t = (el.innerText || el.textContent || '').trim();
                            if (!t) continue;
                            if (t.indexOf(textIncludes) !== -1) {
                                var active = isActive(el);
                                if (shouldBeActive && !active) {
                                    el.click();
                                    log('点击以启用: ' + textIncludes);
                                }
                                if (!shouldBeActive && active) {
                                    el.click();
                                    log('点击以关闭: ' + textIncludes);
                                }
                                return true;
                            }
                        }
                        return false;
                    }

                    function ensureWebSearchMode() {
                        // 目标：联网搜索开；深度思考关
                        // 文案可能为：联网搜索/联网/搜索；深度思考/思考
                        var enabledWeb = clickIfNeededByText('联网搜索', true) ||
                                         clickIfNeededByText('联网', true) ||
                                         clickIfNeededByText('搜索', true);

                        // 尽量关闭“深度思考”
                        clickIfNeededByText('深度思考', false);
                        clickIfNeededByText('思考', false);

                        if (enabledWeb) {
                            log('已尝试启用联网搜索');
                        }
                    }
                    
                    // 提交搜索
                    function submitSearch() {
                        log('尝试提交搜索');
                        
                        // 方法1: 查找发送按钮（通常是输入框旁边的按钮）
                        var textarea = document.querySelector('textarea');
                        if (textarea) {
                            var parent = textarea.parentElement;
                            // 向上查找几层，找到包含按钮的容器
                            for (var i = 0; i < 5 && parent; i++) {
                                var btns = parent.querySelectorAll('button, div[role="button"], [class*="btn"]');
                                for (var j = 0; j < btns.length; j++) {
                                    var btn = btns[j];
                                    var rect = btn.getBoundingClientRect();
                                    // 按钮应该是可见的
                                    if (rect.width > 0 && rect.height > 0) {
                                        // 检查是否是发送按钮（通常包含 svg 或特定样式）
                                        var hasSvg = btn.querySelector('svg');
                                        var isLikelySend = hasSvg || 
                                            btn.innerText.includes('发送') || 
                                            btn.innerText.includes('Send') ||
                                            btn.className.includes('send') ||
                                            btn.className.includes('submit');
                                        
                                        // 排除明显不是发送按钮的（如设置、附件等）
                                        var isNotSend = btn.className.includes('setting') ||
                                            btn.className.includes('attach') ||
                                            btn.className.includes('upload') ||
                                            btn.getAttribute('aria-label')?.includes('设置');
                                        
                                        if (isLikelySend && !isNotSend) {
                                            log('找到可能的发送按钮，尝试点击');
                                            // 避免点击到 disabled/aria-disabled 的按钮
                                            var disabled = btn.disabled || btn.getAttribute('aria-disabled') === 'true';
                                            if (!disabled) {
                                                btn.click();
                                                log('已点击发送按钮');
                                                return;
                                            }
                                            log('发送按钮疑似不可用，改用 Enter');
                                            tryEnterKey();
                                            return;
                                        }
                                    }
                                }
                                parent = parent.parentElement;
                            }
                        }
                        
                        // 方法2: 直接用 Enter 键
                        log('未找到发送按钮，使用 Enter 键');
                        tryEnterKey();
                    }
                    
                    function tryEnterKey() {
                        var textarea = document.querySelector('textarea');
                        if (textarea) {
                            textarea.focus();
                            
                            // 模拟真实的 Enter 键按下
                            ['keydown', 'keypress', 'keyup'].forEach(function(eventType) {
                                var event = new KeyboardEvent(eventType, {
                                    key: 'Enter',
                                    code: 'Enter',
                                    keyCode: 13,
                                    which: 13,
                                    charCode: eventType === 'keypress' ? 13 : 0,
                                    bubbles: true,
                                    cancelable: true,
                                    composed: true
                                });
                                textarea.dispatchEvent(event);
                            });
                            
                            log('已发送 Enter 键事件');
                            
                            // 如果还是没发送，尝试触发表单提交
                            setTimeout(function() {
                                var form = textarea.closest('form');
                                if (form) {
                                    log('尝试提交表单');
                                    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                                }
                            }, 500);
                        }
                    }
                    
                    // 开始执行
                    findAndFillInput();
                })();
            """.trimIndent()
            
            webView?.evaluateJavascript(searchScript, null)
        }, 2000) // 等待2秒让页面加载
    }
    
    /**
     * 获取保存的 Cookie
     */
    fun getCookie(): String {
        return prefs.getString(KEY_COOKIE, "") ?: ""
    }
    
    /**
     * 保存 Cookie
     */
    fun saveCookie(cookie: String) {
        prefs.edit { putString(KEY_COOKIE, cookie) }
        // 如果 WebView 已创建，立即应用 Cookie
        if (webView != null && cookie.isNotEmpty()) {
            setupCookies()
            webView?.reload()
        }
    }
    
    /**
     * 获取是否启用 DeepSeek
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * 设置是否启用 DeepSeek
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) {
            container.visibility = View.VISIBLE
            if (!isInitialized) {
                initialize()
            }
        } else {
            container.visibility = View.GONE
        }
    }
    
    /**
     * 获取展开状态
     */
    fun isExpanded(): Boolean = isExpanded
    
    /**
     * 刷新页面
     */
    fun refresh() {
        webView?.reload()
    }
    
    /**
     * 加载新对话
     */
    fun loadNewChat() {
        webView?.loadUrl(DEEPSEEK_NEW_CHAT_URL)
    }
    
    /**
     * 销毁 WebView
     */
    fun destroy() {
        webView?.let {
            webViewContainer.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        isInitialized = false
    }
    
    /**
     * 暂停 WebView
     */
    fun onPause() {
        webView?.onPause()
    }
    
    /**
     * 恢复 WebView
     */
    fun onResume() {
        webView?.onResume()
    }
}
