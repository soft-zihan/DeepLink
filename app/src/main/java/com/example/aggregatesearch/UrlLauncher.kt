package com.example.aggregatesearch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.example.aggregatesearch.data.SearchUrl

object UrlLauncher {
    
    fun launchSearchUrls(context: Context, searchQuery: String, selectedUrls: List<SearchUrl>) {
        if (selectedUrls.isEmpty()) {
            Toast.makeText(context, "请选择至少一个搜索链接", Toast.LENGTH_SHORT).show()
            return
        }

        for (url in selectedUrls) {
            if (!url.isEnabled && selectedUrls.size > 1) continue

            try {
                val formattedUrl = if (url.urlPattern.contains("%s")) {
                    url.urlPattern.replace("%s", Uri.encode(searchQuery))
                } else {
                    url.urlPattern
                }
                val intent = Intent(Intent.ACTION_VIEW, formattedUrl.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开链接: ${url.name}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}
