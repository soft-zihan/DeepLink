package com.example.aggregatesearch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.aggregatesearch.utils.SearchHistoryManager
import androidx.fragment.app.viewModels
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.aggregatesearch.utils.UiUtils
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import java.lang.Throwable
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.aggregatesearch.BuildConfig

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 应用顶栏颜色
        UiUtils.applyToolbarColor(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "设置"

        // 仅在首次创建时添加 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment()) // 使用布局中的容器
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时重新应用顶栏颜色，确保与主界面保持一致
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

        private var currentPhotoUri: Uri? = null
        private var selectingDarkWallpaper: Boolean = false // Moved here for broader access if needed

        private lateinit var wallpaperActivityResultLauncher: ActivityResultLauncher<Intent>
        private lateinit var cameraActivityResultLauncher: ActivityResultLauncher<Intent>
        private lateinit var ucropActivityResultLauncher: ActivityResultLauncher<Intent> // For UCrop result

        // 添加相机权限请求
        private val requestCameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // 权限被授予，可以打开相机
                launchCamera()
            } else {
                // 权限被拒绝
                Toast.makeText(requireContext(), "需要相机权限才能拍摄照片", Toast.LENGTH_SHORT).show()
            }
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
                requireContext().getSharedPreferences("ui_prefs", 0).edit { putString("toolbar_color", colorValue) }
                activity?.recreate()
                true
            }

            // 壁纸（浅色、深色）
            val lightWallpaperPref = findPreference<Preference>("pref_wallpaper_light")
            val darkWallpaperPref = findPreference<Preference>("pref_wallpaper_dark")

            wallpaperActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) { // Changed from Activity.RESULT_OK
                    result.data?.data?.let { uri ->
                        startCrop(uri) // Launch UCrop
                    }
                }
            }

            cameraActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) { // Changed from Activity.RESULT_OK
                    currentPhotoUri?.let { uri ->
                        startCrop(uri) // Launch UCrop
                    }
                }
            }

            ucropActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val resultUri = result.data?.let { intent -> UCrop.getOutput(intent) as? Uri }
                    if (resultUri != null) {
                        try {
                            saveWallpaperUri(resultUri)
                        } catch (_: ClassCastException) {
                            Toast.makeText(requireContext(), "剪裁结果类型错误", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "剪裁失败", Toast.LENGTH_SHORT).show()
                    }
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    val cropError = result.data?.let { intent -> UCrop.getError(intent) as? Throwable }
                    val errorMessage = cropError?.message ?: "未知剪裁错误"
                    Toast.makeText(requireContext(), "剪裁错误: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }

            // 版本显示
            findPreference<Preference>("pref_version")?.summary = "v" + BuildConfig.VERSION_NAME

            // 检查更新
            findPreference<Preference>("pref_check_update")?.setOnPreferenceClickListener {
                checkLatestVersion()
                true
            }

            lightWallpaperPref?.setOnPreferenceClickListener {
                selectingDarkWallpaper = false
                showWallpaperSourceDialog()
                true
            }

            darkWallpaperPref?.setOnPreferenceClickListener {
                selectingDarkWallpaper = true
                showWallpaperSourceDialog()
                true
            }
        }

        private fun showWallpaperSourceDialog() {
            val options = arrayOf("图库", "文件管理器", "拍摄")
            AlertDialog.Builder(requireContext())
                .setTitle("选择壁纸来源")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openGallery()
                        1 -> openFileManager()
                        2 -> openCamera()
                    }
                }
                .show()
        }

        private fun openGallery() {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            wallpaperActivityResultLauncher.launch(intent)
        }

        private fun openFileManager() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            wallpaperActivityResultLauncher.launch(intent)
        }

        private fun openCamera() {
            // 请求相机权限
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        private fun launchCamera() {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                    val photoFile: File? = try {
                        createImageFile()
                    } catch (_: IOException) { // Changed ex to _
                        Toast.makeText(requireContext(), "失败", Toast.LENGTH_SHORT).show()
                        null
                    }
                    photoFile?.also {
                        val photoURI: Uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${BuildConfig.APPLICATION_ID}.provider",
                            it
                        )
                        currentPhotoUri = photoURI
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        cameraActivityResultLauncher.launch(takePictureIntent)
                    }
                }
            }
        }

        private fun startCrop(sourceUri: Uri) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destinationFileName = "cropped_wallpaper_${timestamp}.jpg"
            // Using cacheDir for the cropped image destination
            val destinationUri = Uri.fromFile(File(requireContext().cacheDir, destinationFileName))

            val options = UCrop.Options()
            // 获取屏幕实际尺寸用于裁剪
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // 美化裁剪UI
            options.setToolbarColor(getToolbarColor(requireContext()))
            options.setStatusBarColor(getToolbarColor(requireContext()))
            options.setActiveControlsWidgetColor(getToolbarColor(requireContext()))
            options.setToolbarWidgetColor(Color.WHITE)

            // 重要：使用withAspectRatio固定裁剪框为屏幕比例
            val ucropIntent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(screenWidth.toFloat(), screenHeight.toFloat()) // 固定裁剪比例为屏幕尺寸
                .withMaxResultSize(screenWidth * 2, screenHeight * 2) // 确保图片质量足够高
                .withOptions(options)
                .getIntent(requireContext())
            ucropActivityResultLauncher.launch(ucropIntent)
        }

        // 获取工具栏颜色以保持UI一致性
        private fun getToolbarColor(context: Context): Int {
            val colorStr = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                .getString("toolbar_color", "#6200EE") ?: "#6200EE"
            return Color.parseColor(colorStr)
        }

        @Throws(IOException::class)
        private fun createImageFile(): File {
            // Create an image file name
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File? = requireActivity().getExternalFilesDir(null) // Use app-specific directory
            return File.createTempFile(
                "JPEG_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
            ).apply {
                // Save a file: path for use with ACTION_VIEW intents
                // currentPhotoPath = absolutePath // Not needed if using URI directly
            }
        }

        // private var selectingDarkWallpaper: Boolean = false // Moved to class level

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

        private fun createBackupData(): JSONObject { // Changed from org.json.JSONObject
            val rootObject = JSONObject() // Changed from org.json.JSONObject

            val groupsArray = org.json.JSONArray()
            searchViewModel.allGroups.value.forEach { group ->
                val groupObject = JSONObject().apply { // Changed from org.json.JSONObject
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
                val urlObject = JSONObject().apply { // Changed from org.json.JSONObject
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
            val rootObject = JSONObject(jsonString) // Changed from org.json.JSONObject

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
                        id = urlObject.getLong("id"),
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
                                    val intent = Intent(Intent.ACTION_VIEW, htmlUrl.toUri()) // Changed from Uri.parse(htmlUrl)
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

        private fun saveWallpaperUri(uri: Uri) { // Renamed from handleWallpaperSelected
            val key = if (selectingDarkWallpaper) PREF_WALLPAPER_DARK_URI else PREF_WALLPAPER_LIGHT_URI
            try {
                // Request persistent read permission only for content URIs
                if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                }
            } catch (_: SecurityException) { // Changed e to _
                // already persisted or not grantable, or it's a file URI from cache
                // Toast.makeText(requireContext(), "无法获取图片权限: ${e.message}", Toast.LENGTH_SHORT).show()
                // Log.e("SettingsFragment", "Error taking persistable URI permission", e)
            }
            requireContext().getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE) // Changed from Context.MODE_PRIVATE
                .edit { putString(key, uri.toString()) } // Changed to KTX
            Toast.makeText(requireContext(), "壁纸已设置", Toast.LENGTH_SHORT).show()
            // The wallpaper will be applied when MainActivity resumes or if UiUtils.applyWallpaper is called.
        }
    }
}
