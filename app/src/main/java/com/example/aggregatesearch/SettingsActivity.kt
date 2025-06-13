package com.example.aggregatesearch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.aggregatesearch.utils.SearchHistoryManager
import androidx.fragment.app.viewModels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.aggregatesearch.utils.UiUtils
import android.content.Context
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.app.AlertDialog
import com.example.aggregatesearch.BuildConfig

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "设置"

        UiUtils.applyToolbarColor(this)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        UiUtils.applyToolbarColor(this)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var searchHistoryManager: SearchHistoryManager

        private val searchViewModel: SearchViewModel by viewModels {
            SearchViewModelFactory((requireActivity().application as SearchApplication).repository)
        }

        private val createBackupFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri?.let { exportBackup(it) }
        }

        private val openRestoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { importBackup(it) }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            searchHistoryManager = SearchHistoryManager(requireContext())

            // 搜索历史开关
            val historySwitch = findPreference<SwitchPreferenceCompat>("pref_history")
            historySwitch?.apply {
                isChecked = searchHistoryManager.isHistoryEnabled()
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    searchHistoryManager.setHistoryEnabled(enabled)
                    true
                }
            }

            // 备份与恢复
            findPreference<Preference>("pref_backup")?.setOnPreferenceClickListener {
                createBackupFile.launch("deeplink_backup.json")
                true
            }
            findPreference<Preference>("pref_restore")?.setOnPreferenceClickListener {
                openRestoreFile.launch(arrayOf("application/json"))
                true
            }

            // 顶栏颜色修改: ListPreference
            val colorPref = findPreference<ListPreference>("pref_toolbar_color")
            colorPref?.setOnPreferenceChangeListener { _, newValue ->
                // 保存到 SharedPreferences
                val colorValue = newValue as String
                requireContext().getSharedPreferences("ui_prefs", 0).edit().putString("toolbar_color", colorValue).apply()
                activity?.recreate()
                true
            }

            // 壁纸（浅色、深色）
            val lightWallpaperPref = findPreference<Preference>("pref_wallpaper_light")
            val darkWallpaperPref = findPreference<Preference>("pref_wallpaper_dark")

            // 版本显示
            findPreference<Preference>("pref_version")?.summary = "v" + BuildConfig.VERSION_NAME

            // 检查更新
            findPreference<Preference>("pref_check_update")?.setOnPreferenceClickListener {
                checkLatestVersion()
                true
            }

            val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    val isDark = selectingDarkWallpaper
                    val key = if (isDark) PREF_WALLPAPER_DARK_URI else PREF_WALLPAPER_LIGHT_URI
                    // 请求持久化读取权限
                    try {
                        requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        // already persisted or not grantable, ignore
                    }
                    requireContext().getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
                        .edit().putString(key, uri.toString()).apply()
                }
            }

            lightWallpaperPref?.setOnPreferenceClickListener {
                selectingDarkWallpaper = false
                wallpaperLauncher.launch(arrayOf("image/*"))
                true
            }

            darkWallpaperPref?.setOnPreferenceClickListener {
                selectingDarkWallpaper = true
                wallpaperLauncher.launch(arrayOf("image/*"))
                true
            }
        }

        private var selectingDarkWallpaper: Boolean = false

        companion object {
            private const val WALLPAPER_PREFS = "wallpaper_prefs"
            private const val PREF_WALLPAPER_LIGHT_URI = "wallpaper_light_uri"
            private const val PREF_WALLPAPER_DARK_URI = "wallpaper_dark_uri"
        }

        private fun exportBackup(uri: Uri) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val backupData = createBackupData()
                    outputStream.write(backupData.toString(2).toByteArray())
                    Toast.makeText(requireContext(), "备份成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun importBackup(uri: Uri) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().readText()
                    restoreFromBackup(jsonString)
                    Toast.makeText(requireContext(), "恢复成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun createBackupData(): org.json.JSONObject {
            val rootObject = org.json.JSONObject()

            val groupsArray = org.json.JSONArray()
            searchViewModel.allGroups.value.forEach { group ->
                val groupObject = org.json.JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("isExpanded", group.isExpanded)
                    put("orderIndex", group.orderIndex)
                }
                groupsArray.put(groupObject)
            }
            rootObject.put("groups", groupsArray)

            val urlsArray = org.json.JSONArray()
            searchViewModel.allUrls.value.forEach { url ->
                val urlObject = org.json.JSONObject().apply {
                    put("id", url.id)
                    put("name", url.name)
                    put("urlPattern", url.urlPattern)
                    put("isEnabled", url.isEnabled)
                    put("orderIndex", url.orderIndex)
                    put("groupId", url.groupId)
                    put("packageName", url.packageName)
                }
                urlsArray.put(urlObject)
            }
            rootObject.put("urls", urlsArray)

            return rootObject
        }

        private fun restoreFromBackup(jsonString: String) {
            val rootObject = org.json.JSONObject(jsonString)

            val groupsArray = rootObject.getJSONArray("groups")
            val groups = mutableListOf<com.example.aggregatesearch.data.UrlGroup>()
            for (i in 0 until groupsArray.length()) {
                val groupObject = groupsArray.getJSONObject(i)
                groups.add(
                    com.example.aggregatesearch.data.UrlGroup(
                        id = groupObject.getLong("id"),
                        name = groupObject.getString("name"),
                        isExpanded = groupObject.getBoolean("isExpanded"),
                        orderIndex = groupObject.getInt("orderIndex")
                    )
                )
            }

            val urlsArray = rootObject.getJSONArray("urls")
            val urls = mutableListOf<com.example.aggregatesearch.data.SearchUrl>()
            for (i in 0 until urlsArray.length()) {
                val urlObject = urlsArray.getJSONObject(i)
                urls.add(
                    com.example.aggregatesearch.data.SearchUrl(
                        id = urlObject.getInt("id"),
                        name = urlObject.getString("name"),
                        urlPattern = urlObject.getString("urlPattern"),
                        isEnabled = urlObject.getBoolean("isEnabled"),
                        orderIndex = urlObject.getInt("orderIndex"),
                        groupId = urlObject.getLong("groupId"),
                        packageName = if (urlObject.has("packageName")) urlObject.getString("packageName") else ""
                    )
                )
            }

            searchViewModel.restoreFromBackup(groups, urls)
        }

        private fun checkLatestVersion() {
            // 使用协程避免阻塞
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("https://api.github.com/repos/soft-zihan/DeepLink/releases/latest")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Accept", "application/vnd.github+json")

                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val latestTag = json.optString("tag_name", json.optString("name", ""))
                    val htmlUrl = json.optString("html_url")

                    withContext(Dispatchers.Main) {
                        val current = BuildConfig.VERSION_NAME
                        if (latestTag.isNotEmpty() && latestTag.removePrefix("v") > current) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("发现新版本 $latestTag")
                                .setMessage("是否前往下载？")
                                .setPositiveButton("打开网页") { _, _ ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl))
                                    startActivity(intent)
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        } else {
                            Toast.makeText(requireContext(), "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "检查更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
} 