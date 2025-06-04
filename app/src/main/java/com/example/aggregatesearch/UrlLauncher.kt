package com.example.aggregatesearch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
                    if (searchQuery.isEmpty()) {
                        // 当搜索查询为空时，直接删除%s
                        url.urlPattern.replace("%s", "")
                    } else {
                        // 有搜索查询时，正常替换
                        url.urlPattern.replace("%s", Uri.encode(searchQuery))
                    }
                } else {
                    url.urlPattern
                }

                val intent = Intent(Intent.ACTION_VIEW, formattedUrl.toUri())

                // 如果指定了包名，并且该包已安装，则使用指定的应用打开
                if (url.packageName.isNotEmpty()) {
                    val packageManager = context.packageManager
                    if (isPackageInstalled(url.packageName, packageManager)) {
                        intent.setPackage(url.packageName)
                    } else {
                        Toast.makeText(context, "指定的应用未安装: ${url.packageName}", Toast.LENGTH_SHORT).show()
                    }
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "无法打开链接: ${url.name}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // 检查应用是否已安装
    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
