package com.example.aggregatesearch.browser

data class BrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val isMainTab: Boolean = false
) {
    companion object {
        fun createMainTab(): BrowserTab {
            return BrowserTab(
                id = "main",
                title = "主页",
                url = "",
                isMainTab = true
            )
        }

        fun createWebTab(url: String, title: String = "新标签"): BrowserTab {
            return BrowserTab(
                id = System.currentTimeMillis().toString(),
                title = title,
                url = url,
                isMainTab = false
            )
        }
    }
}
