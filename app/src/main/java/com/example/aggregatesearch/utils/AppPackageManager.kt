package com.example.aggregatesearch.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工具类，用于获取设备中已安装的应用列表
 */
class AppPackageManager(private val context: Context) {

    /**
     * 表示应用信息的数据类
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    /**
     * 获取设备上所有已安装的应用信息
     * 此操作可能很耗时，应在后台线程中执行
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        installedApps.map { applicationInfo ->
            AppInfo(
                packageName = applicationInfo.packageName,
                appName = applicationInfo.loadLabel(packageManager).toString(),
                isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.appName }
    }

    /**
     * 获取设备上所有已安装的非系统应用信息
     */
    suspend fun getNonSystemApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getInstalledApps().filter { !it.isSystemApp }
    }

    /**
     * 根据包名获取应用名称
     */
    fun getAppNameByPackage(packageName: String): String? {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 搜索包名或应用名称包含特定关键字的应用
     */
    suspend fun searchApps(query: String): List<AppInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }

        val lowerCaseQuery = query.lowercase()
        getInstalledApps().filter {
            it.appName.lowercase().contains(lowerCaseQuery) ||
            it.packageName.lowercase().contains(lowerCaseQuery)
        }
    }
}
