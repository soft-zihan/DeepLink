/**
 * 备份恢复管理器
 * 负责处理应用数据的备份导出和恢复导入功能
 * 包括：JSON格式的数据备份、从文件恢复数据、数据格式转换等
 */
package com.example.aggregatesearch.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.example.aggregatesearch.SearchViewModel
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.UrlGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class BackupRestoreManager(
    private val context: Context,
    private val searchViewModel: SearchViewModel
) {

    fun exportBackup(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val backupData = createBackupData()
                outputStream.write(backupData.toString(2).toByteArray())
                Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importBackup(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = readTextFromStream(inputStream)
                restoreFromBackup(jsonString)
                Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createBackupData(): JSONObject {
        val rootObject = JSONObject()

        // 备份分组数据
        val groupsArray = JSONArray()
        searchViewModel.allGroups.value.forEach { group ->
            val groupObject = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("isExpanded", group.isExpanded)
                put("orderIndex", group.orderIndex)
                put("color", group.color)
            }
            groupsArray.put(groupObject)
        }
        rootObject.put("groups", groupsArray)

        // 备份链接数据
        val urlsArray = JSONArray()
        searchViewModel.allUrls.value.forEach { url ->
            val urlObject = JSONObject().apply {
                put("id", url.id)
                put("name", url.name)
                put("urlPattern", url.urlPattern)
                put("isEnabled", url.isEnabled)
                put("orderIndex", url.orderIndex)
                put("groupId", url.groupId)
                put("packageName", url.packageName)
                put("useTextIcon", url.useTextIcon)
                put("iconText", url.iconText)
                put("iconBackgroundColor", url.iconBackgroundColor)
            }
            urlsArray.put(urlObject)
        }
        rootObject.put("urls", urlsArray)

        // 添加备份元数据
        rootObject.put("backupVersion", "1.0")
        rootObject.put("backupTime", System.currentTimeMillis())

        return rootObject
    }

    private fun restoreFromBackup(jsonString: String) {
        val rootObject = JSONObject(jsonString)

        // 恢复分组数据
        val groupsArray = rootObject.getJSONArray("groups")
        val groups = mutableListOf<UrlGroup>()
        for (i in 0 until groupsArray.length()) {
            val groupObject = groupsArray.getJSONObject(i)
            groups.add(
                UrlGroup(
                    id = groupObject.getLong("id"),
                    name = groupObject.getString("name"),
                    isExpanded = groupObject.optBoolean("isExpanded", true),
                    orderIndex = groupObject.optInt("orderIndex", i),
                    color = groupObject.optString("color", null)
                )
            )
        }

        // 恢复链接数据
        val urlsArray = rootObject.getJSONArray("urls")
        val urls = mutableListOf<SearchUrl>()
        for (i in 0 until urlsArray.length()) {
            val urlObject = urlsArray.getJSONObject(i)
            urls.add(
                SearchUrl(
                    id = urlObject.getLong("id"),
                    name = urlObject.getString("name"),
                    urlPattern = urlObject.getString("urlPattern"),
                    isEnabled = urlObject.optBoolean("isEnabled", true),
                    orderIndex = urlObject.optInt("orderIndex", i),
                    groupId = urlObject.getLong("groupId"),
                    packageName = urlObject.optString("packageName", ""),
                    useTextIcon = urlObject.optBoolean("useTextIcon", false),
                    iconText = urlObject.optString("iconText", ""),
                    iconBackgroundColor = urlObject.optInt("iconBackgroundColor", 0xFF2196F3.toInt())
                )
            )
        }

        // 提交恢复的数据到ViewModel
        searchViewModel.restoreFromBackup(groups, urls)
    }

    private fun readTextFromStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line)
        }
        return stringBuilder.toString()
    }

    /**
     * 验证备份文件格式是否正确
     */
    fun validateBackupFile(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = readTextFromStream(inputStream)
                val rootObject = JSONObject(jsonString)
                rootObject.has("groups") && rootObject.has("urls")
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取备份文件信息
     */
    fun getBackupInfo(uri: Uri): BackupInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = readTextFromStream(inputStream)
                val rootObject = JSONObject(jsonString)
                val groupsArray = rootObject.getJSONArray("groups")
                val urlsArray = rootObject.getJSONArray("urls")
                val backupTime = rootObject.optLong("backupTime", 0L)
                val backupVersion = rootObject.optString("backupVersion", "未知")

                BackupInfo(
                    groupCount = groupsArray.length(),
                    urlCount = urlsArray.length(),
                    backupTime = backupTime,
                    version = backupVersion
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    data class BackupInfo(
        val groupCount: Int,
        val urlCount: Int,
        val backupTime: Long,
        val version: String
    )
}
