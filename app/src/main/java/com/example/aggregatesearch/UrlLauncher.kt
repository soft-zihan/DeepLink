package com.example.aggregatesearch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.example.aggregatesearch.data.SearchUrl

object UrlLauncher {
    
    private const val TAG = "UrlLauncher"

    fun launchSearchUrls(context: Context, searchQuery: String, selectedUrls: List<SearchUrl>) {
        if (selectedUrls.isEmpty()) {
            Toast.makeText(context, "请选择至少一个搜索链接", Toast.LENGTH_SHORT).show()
            return
        }

        var launchCount = 0
        for (url in selectedUrls) {
            if (!url.isEnabled && selectedUrls.size > 1) continue

            try {
                // 如果链接为空，尝试直接使用包名启动应用
                if (url.urlPattern.isEmpty()) {
                    if (url.packageName.isNotEmpty()) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(url.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            launchCount++
                        } else {
                            showError(context, "无法启动应用: ${url.name} (${url.packageName})")
                        }
                    } else {
                        showError(context, "未配置URL或包名: ${url.name}")
                    }
                    continue
                }

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

                try {
                    val uri = formattedUrl.toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)

                    // 如果指定了包名，并且该包已安装，则使用指定的应用打开
                    if (url.packageName.isNotEmpty()) {
                        val packageManager = context.packageManager
                        if (isPackageInstalled(url.packageName, packageManager)) {
                            intent.setPackage(url.packageName)
                        } else {
                            showError(context, "指定的应用未安装: ${url.name} (${url.packageName})")
                            // 尝试不指定包名继续打开
                            val genericIntent = Intent(Intent.ACTION_VIEW, uri)
                            genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(genericIntent)
                            launchCount++
                            continue
                        }
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    launchCount++
                } catch (e: Exception) {
                    Log.e(TAG, "URL格式错误: $formattedUrl", e)
                    showError(context, "URL格式错误: ${url.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动错误: ${url.name}", e)
                showError(context, "无法打开链接: ${url.name}")
            }
        }

        if (launchCount == 0 && selectedUrls.isNotEmpty()) {
            Toast.makeText(context, "没有成功启动任何链接，请检查配置", Toast.LENGTH_LONG).show()
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

    private fun showError(context: Context, message: String) {
        Log.w(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
