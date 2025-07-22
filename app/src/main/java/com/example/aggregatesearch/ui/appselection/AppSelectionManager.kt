/**
 * 应用选择管理器
 * 负责处理应用选择相关的功能
 * 包括：应用选择结果回调处理、应用信息获取、包名验证等
 */
package com.example.aggregatesearch.ui.appselection

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import com.example.aggregatesearch.R

class AppSelectionManager(
    private val context: Context,
    private val appSelectionResultLauncher: ActivityResultLauncher<Intent>
) {

    private var currentPackageNameEditText: EditText? = null
    private var currentSelectedAppNameTextView: TextView? = null
    private var onAppSelectedGlobal: ((String, String) -> Unit)? = null

    fun setupAppSelectionCallback() {
        // 注册应用选择结果回调在MainActivity中已完成
        // 这里提供处理回调的方法
    }

    fun handleAppSelectionResult(packageName: String, appName: String) {
        onAppSelectedGlobal?.invoke(packageName, appName)
    }

    fun setupAppSelection(
        packageNameEditText: EditText,
        selectedAppNameTextView: TextView,
        onAppSelected: (String, String) -> Unit
    ) {
        currentPackageNameEditText = packageNameEditText
        currentSelectedAppNameTextView = selectedAppNameTextView
        onAppSelectedGlobal = { packageName, appName ->
            onAppSelected(packageName, appName)
            currentPackageNameEditText?.setText(packageName)
            if (packageName.isNotEmpty()) {
                currentSelectedAppNameTextView?.text = context.getString(R.string.selected_app_format, appName)
                currentSelectedAppNameTextView?.visibility = View.VISIBLE
            } else {
                currentSelectedAppNameTextView?.visibility = View.GONE
            }
        }
    }

    fun launchAppSelection() {
        val intent = Intent(context, com.example.aggregatesearch.activities.AppSelectionActivity::class.java)
        appSelectionResultLauncher.launch(intent)
    }

    fun clearAppBinding() {
        currentPackageNameEditText?.setText("")
        currentSelectedAppNameTextView?.visibility = View.GONE
        onAppSelectedGlobal?.invoke("", "")
    }

    /**
     * 验证包名是否有效
     */
    fun isPackageNameValid(packageName: String): Boolean {
        if (packageName.isEmpty()) return true // 空包名是允许的

        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取应用名称
     */
    fun getAppName(packageName: String): String? {
        return try {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 获取应用图标
     */
    fun getAppIcon(packageName: String): android.graphics.drawable.Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * 检查应用是否已安装
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取所有已安装的应用信息
     */
    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps.map { appInfo ->
            AppInfo(
                packageName = appInfo.packageName,
                appName = packageManager.getApplicationLabel(appInfo).toString(),
                icon = packageManager.getApplicationIcon(appInfo)
            )
        }.sortedBy { it.appName }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )
}
