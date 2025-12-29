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

        fun createWebTab(url: String, title: String?): BrowserTab {
            return BrowserTab(
                id = System.currentTimeMillis().toString(),
                title = title ?: "新标签",
                url = url,
                isMainTab = false
            )
        }
    }
}
