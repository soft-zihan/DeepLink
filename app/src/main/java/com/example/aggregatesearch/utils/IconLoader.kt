package com.example.aggregatesearch.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.aggregatesearch.R
import com.example.aggregatesearch.data.SearchUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object IconLoader {
    private const val TAG = "IconLoader"

    // 图标内存缓存，提高性能
    private val iconCache = LruCache<String, Drawable>(50)

    // 添加图标加载失败的回调接口
    interface IconLoadListener {
        fun onIconLoadFailed(searchUrl: SearchUrl)
    }

    // 默认为空实现
    private var iconLoadListener: IconLoadListener? = null

    // 设置图标加载失败的监听器
    fun setIconLoadListener(listener: IconLoadListener) {
        iconLoadListener = listener
    }

    fun loadIcon(context: Context, searchUrl: SearchUrl, imageView: ImageView) {
        try {
            val cacheKey = "${searchUrl.id}|${searchUrl.urlPattern}|${searchUrl.packageName}|${searchUrl.useTextIcon}|${searchUrl.iconText}|${searchUrl.iconBackgroundColor}"

            // 1. 先检查内存缓存
            iconCache.get(cacheKey)?.let { cachedIcon ->
                imageView.setImageDrawable(cachedIcon)
                return
            }

            // 2. 尝试从本地存储加载
            val localIcon = if (searchUrl.useTextIcon) {
                // 文字图标不从本地加载，每次重新创建以确保最新设置生效
                createTextIcon(context, cleanIconText(searchUrl.iconText), searchUrl.iconBackgroundColor)
            } else {
                loadIconFromLocal(context, cacheKey)
            }

            if (localIcon != null) {
                // 本��存储有图标，加载并缓存到内存
                imageView.setImageDrawable(localIcon)
                iconCache.put(cacheKey, localIcon)
                return
            }

            // 3. 从网络或系统获取图标
            CoroutineScope(Dispatchers.IO).launch {
                var icon: Drawable? = null
                var iconLoadFailed = false

                // 尝试获取网络图标或系统图标
                if (!searchUrl.useTextIcon) {
                    try {
                        icon = getIconForUrl(context, searchUrl.urlPattern, searchUrl.packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load icon: ${e.message}")
                        iconLoadFailed = true
                        // 获取图标失败时不设置图标，继续处理，下面会自动回退到文字图标
                    }

                    // 检测是否获取到了默认图标
                    val default1 = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search)?.constantState
                    val default2 = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)?.constantState

                    // 如果获取失败或者是默认图标，自动使用文字图标作为备选
                    if (icon == null || icon.constantState == default1 || icon.constantState == default2) {
                        iconLoadFailed = true
                        // 设置文字图标，使用完整的链接名称作为文本
                        val textForIcon = searchUrl.name // 使用完整名称作为文字图标
                        icon = createTextIcon(context, textForIcon, searchUrl.iconBackgroundColor)
                    }
                } else {
                    // 用户明确选择了文字图标
                    // 确保在没有设置iconText时使用链接的完整名称
                    val textForIcon = if (searchUrl.iconText.isNotBlank()) {
                        cleanIconText(searchUrl.iconText)
                    } else {
                        // 使用链接的名称作为默认文本（完整名称，不仅是首字母）
                        searchUrl.name
                    }
                    icon = createTextIcon(context, textForIcon, searchUrl.iconBackgroundColor)
                }

                // 确保获取到了图标
                if (icon == null) {
                    icon = createTextIcon(context, searchUrl.name, searchUrl.iconBackgroundColor)
                }

                // 存入内存缓存
                iconCache.put(cacheKey, icon)

                // 如果不是文字图标，保存到本地存储
                if (!searchUrl.useTextIcon) {
                    saveIconToLocal(context, icon, cacheKey)
                }

                // 如果图标加载失败并且不是文字图标模式，通知监听器
                if (iconLoadFailed && !searchUrl.useTextIcon) {
                    iconLoadListener?.onIconLoadFailed(searchUrl)
                }

                withContext(Dispatchers.Main) {
                    try {
                        // 防止在异步加载过程中ImageView被回收的情况
                        if (imageView.isAttachedToWindow) {
                            imageView.setImageDrawable(icon)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting image drawable: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadIcon: ${e.message}")
            // 出错时显示默认图标，避免闪退
            try {
                // 即使在这里出错，也使用搜索链接名称显示文字图标
                val textIcon = createTextIcon(context, searchUrl.name, Color.GRAY)
                imageView.setImageDrawable(textIcon)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set text icon, falling back to default: ${e2.message}")
                try {
                    imageView.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search))
                } catch (e3: Exception) {
                    // 确保不会崩退
                    Log.e(TAG, "Failed to set any icon: ${e3.message}")
                }
            }
        }
    }

    suspend fun loadIconDrawable(context: Context, searchUrl: SearchUrl): Drawable? {
        val cacheKey = "${searchUrl.id}|${searchUrl.urlPattern}|${searchUrl.packageName}|${searchUrl.useTextIcon}|${searchUrl.iconText}|${searchUrl.iconBackgroundColor}"
        return iconCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            val icon = if (searchUrl.useTextIcon) {
                createTextIcon(context, cleanIconText(searchUrl.iconText), searchUrl.iconBackgroundColor)
            } else {
                getIconForUrl(context, searchUrl.urlPattern, searchUrl.packageName)
            }
            iconCache.put(cacheKey, icon)
            icon
        }
    }

    // 为了保持兼容性，保留原来的方法
    fun loadIcon(context: Context, urlPattern: String, packageName: String, imageView: ImageView) {
        try {
            val cacheKey = "$urlPattern|$packageName"

            // 先检查内存缓存
            iconCache.get(cacheKey)?.let { cachedIcon ->
                imageView.setImageDrawable(cachedIcon)
                return
            }

            // 尝试从本地存储加载
            val localIcon = loadIconFromLocal(context, cacheKey)
            if (localIcon != null) {
                imageView.setImageDrawable(localIcon)
                iconCache.put(cacheKey, localIcon)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val icon = getIconForUrl(context, urlPattern, packageName)
                // 存入缓存
                iconCache.put(cacheKey, icon)
                // 保存到本地
                saveIconToLocal(context, icon, cacheKey)

                withContext(Dispatchers.Main) {
                    try {
                        if (imageView.isAttachedToWindow) {
                            imageView.setImageDrawable(icon)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting image drawable: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadIcon with pattern: ${e.message}")
            try {
                imageView.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search))
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set default icon: ${e2.message}")
            }
        }
    }

    // 清理图标文字，不再提取首字符，而是使用完整文本
    private fun cleanIconText(text: String): String {
        // 只清理括号内容，保留完整文本名
        return text.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
    }

    // 从本地存储加载图标
    private fun loadIconFromLocal(context: Context, cacheKey: String): Drawable? {
        try {
            val iconHash = hashKey(cacheKey)
            val iconFile = File(context.filesDir, "icons/$iconHash.png")

            if (!iconFile.exists()) return null

            return BitmapDrawable(context.resources, BitmapFactory.decodeFile(iconFile.path))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading icon from local storage: ${e.message}")
            return null
        }
    }

    // 保存图标到本地存储
    private fun saveIconToLocal(context: Context, drawable: Drawable, cacheKey: String) {
        try {
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    // 将其他Drawable类型转换为Bitmap
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }

            val iconHash = hashKey(cacheKey)
            val iconDir = File(context.filesDir, "icons")
            if (!iconDir.exists()) {
                iconDir.mkdirs()
            }

            val iconFile = File(iconDir, "$iconHash.png")
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.d(TAG, "Icon saved to local storage: $iconHash")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving icon to local storage: ${e.message}")
        }
    }

    // 哈希函数，用于生成文件名
    private fun hashKey(key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(key.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    // 将此方法设为 internal，以便在模块内其他地方（如MainActivity）调用
    internal fun createTextIcon(context: Context, text: String, backgroundColor: Int): Drawable {
        val iconSize = 128 // 图标尺寸
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 设置背景
        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, iconSize.toFloat(), iconSize.toFloat(), backgroundPaint)

        // 设置文字
        val textPaint = Paint().apply {
            // 根据背景色的亮度决定文字颜色
            color = if (isColorTooLight(backgroundColor)) Color.BLACK else Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // 处理文字绘制
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) {
            // 如果没有文字，直接返回背景
            return BitmapDrawable(context.resources, bitmap)
        }

        // 动态计算文字大小以适应图标
        val maxTextWidth = iconSize * 0.85f // 文字最大宽度为图标宽度的85%
        val maxTextHeight = iconSize * 0.85f // 文字最大高度为图标高度的85%

        // 根据文本长度初始化字体大小
        var textSize = when {
            cleanedText.length == 1 -> iconSize * 0.6f
            cleanedText.length == 2 -> iconSize * 0.45f
            cleanedText.length <= 4 -> iconSize * 0.35f
            cleanedText.length <= 8 -> iconSize * 0.25f
            else -> iconSize * 0.2f
        }
        textPaint.textSize = textSize

        // 测量文字尺寸
        val textBounds = Rect()
        textPaint.getTextBounds(cleanedText, 0, cleanedText.length, textBounds)

        // 分行处理
        val lines = mutableListOf<String>()

        // 如果文本较长或包含空格，尝试分行
        if (cleanedText.length > 5 || cleanedText.contains(" ")) {
            val words = cleanedText.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                textPaint.getTextBounds(testLine, 0, testLine.length, textBounds)

                if (textBounds.width() <= maxTextWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                    currentLine = word
                }
            }

            // 添加最后一行
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }

        // 如果没有分行或分行后仍然是空，直接作为一行处理
        if (lines.isEmpty()) {
            lines.add(cleanedText)
        }

        // 如果文本被分成了多行，重新计算每行的字体大小
        if (lines.size > 1) {
            textSize = minOf(textSize, iconSize * 0.7f / lines.size)
            textPaint.textSize = textSize
        }

        // 确保文字不会溢出
        var scaleFactor = 1.0f
        for (line in lines) {
            textPaint.getTextBounds(line, 0, line.length, textBounds)
            if (textBounds.width() > maxTextWidth) {
                val lineScaleFactor = maxTextWidth / textBounds.width()
                if (lineScaleFactor < scaleFactor) {
                    scaleFactor = lineScaleFactor
                }
            }
        }

        // 应用缩放因子
        if (scaleFactor < 1.0f) {
            textSize *= scaleFactor
            textPaint.textSize = textSize
        }

        // 绘制文本
        val lineHeight = textPaint.fontSpacing
        val totalTextHeight = lineHeight * lines.size
        var yPos = (iconSize - totalTextHeight) / 2 + textPaint.textSize // 起始Y位置

        for (line in lines) {
            canvas.drawText(line, iconSize / 2f, yPos, textPaint)
            yPos += lineHeight
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    // 公共方法：创建文字图标Drawable
    fun createTextIconDrawable(context: Context, text: String, backgroundColor: Int): Drawable {
        return createTextIcon(context, cleanIconText(text), backgroundColor)
    }

    // 判断颜色是否过亮
    private fun isColorTooLight(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance > 0.7 // 阈值调整为0.7
    }

    // 根据URL和包名获取图标
    suspend fun getIconForUrl(context: Context, urlPattern: String, packageName: String): Drawable {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 优先使用指定的包名获取应用图标
                if (packageName.isNotEmpty()) {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        return@withContext pm.getApplicationIcon(appInfo)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.d(TAG, "Package not found: $packageName, falling back to other methods.")
                    }
                }

                // 2. 如果是 Intent 链接，直接解析获取图标
                if (urlPattern.startsWith("intent:")) {
                    try {
                        val intent = Intent.parseUri(urlPattern, Intent.URI_INTENT_SCHEME)
                        intent.`package`?.let { pkg ->
                            val pm = context.packageManager
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            return@withContext pm.getApplicationIcon(appInfo)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse intent URI or get icon: $urlPattern", e)
                    }
                }

                // 3. 如果是app链接，尝试从链接中提取包名
                if (urlPattern.contains("://")) {
                    val scheme = urlPattern.substringBefore("://").lowercase()
                    if (!scheme.startsWith("http")) {
                        val extractedPackageName = getPackageNameFromScheme(context, scheme)
                        if (extractedPackageName.isNotEmpty()) {
                            try {
                                val pm = context.packageManager
                                val appInfo = pm.getApplicationInfo(extractedPackageName, 0)
                                return@withContext pm.getApplicationIcon(appInfo)
                            } catch (e: PackageManager.NameNotFoundException) {
                                Log.d(TAG, "Extracted package not found: $extractedPackageName")
                            }
                        }
                    }
                }

                // 4. 如果是网页链接，尝试获取网站favicon
                if (urlPattern.startsWith("http")) {
                    val uri = Uri.parse(urlPattern)
                    val domain = uri.host
                    if (domain != null) {
                        val faviconSources = listOf(
                            "https://icons.duckduckgo.com/ip3/$domain.ico",
                            "https://api.faviconkit.com/$domain/128",
                            "${uri.scheme}://$domain/favicon.ico"
                        )

                        for (faviconUrl in faviconSources) {
                            val favicon = downloadFavicon(faviconUrl)
                            if (favicon != null) {
                                return@withContext BitmapDrawable(context.resources, favicon)
                            }
                        }
                    }
                }

                // 5. 如果都失败，尝试通过 scheme 查询应用
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlPattern.replace("%s", "test")))
                    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        return@withContext resolveInfo.loadIcon(context.packageManager)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not resolve activity for scheme in $urlPattern")
                }


                // 6. 如果都失败，返回默认图标
                Log.w(TAG, "Failed to get icon for $urlPattern. Using default.")
                return@withContext ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search)
                    ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
            } catch (e: Exception) {
                Log.e(TAG, "Error getting icon for URL: ${e.message}")
                return@withContext ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search)
                    ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
            }
        }
    }

    // 强制更新图标，忽略缓存
    suspend fun forceUpdateIcon(context: Context, searchUrl: SearchUrl): Drawable? {
        val cacheKey = "${searchUrl.id}|${searchUrl.urlPattern}|${searchUrl.packageName}|${searchUrl.useTextIcon}|${searchUrl.iconText}|${searchUrl.iconBackgroundColor}"
        val icon = getIconForUrl(context, searchUrl.urlPattern, searchUrl.packageName)
        iconCache.put(cacheKey, icon)
        saveIconToLocal(context, icon, cacheKey)
        return icon
    }

    private fun getPackageNameFromScheme(context: Context, scheme: String): String {
        // 常见的app scheme到包名的映射
        val commonSchemes = mapOf(
            "bilibili" to "tv.danmaku.bili",
            "wechat" to "com.tencent.mm",
            "alipay" to "com.eg.android.AlipayGphone",
            "taobao" to "com.taobao.taobao",
            "tmall" to "com.tmall.wireless",
            "jd" to "com.jingdong.app.mall",
            "douyin" to "com.ss.android.ugc.aweme",
            "tiktok" to "com.zhiliaoapp.musically",
            "netease-cloud-music" to "com.netease.cloudmusic",
            "qqmusic" to "com.tencent.qqmusic",
            "baidumap" to "com.baidu.BaiduMap",
            "amap" to "com.autonavi.minimap",
            "qq" to "com.tencent.mobileqq",
            "zhihu" to "com.zhihu.android",
            "weibo" to "com.sina.weibo",
            // 添加拼多多和其它常用应用
            "pinduoduo" to "com.xunmeng.pinduoduo",
            "pdd" to "com.xunmeng.pinduoduo",
            "kwai" to "com.smile.gifmaker",
            "kuaishou" to "com.smile.gifmaker",
            "meituan" to "com.sankuai.meituan",
            "eleme" to "me.ele",
            "dianping" to "com.dianping.v1"
        )

        // 尝试从常见列表中获取
        commonSchemes[scheme]?.let { return it }

        // 尝试查找包含该scheme的应用
        try {
            val pm = context.packageManager
            val resolveInfo = pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://test")),
                0
            )
            if (resolveInfo.isNotEmpty()) {
                return resolveInfo[0].activityInfo.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying intent activities: ${e.message}")
        }

        return ""
    }

    private fun downloadFavicon(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val urlConnection = URL(url)
            connection = urlConnection.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream: InputStream = connection.inputStream
                BitmapFactory.decodeStream(inputStream)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download favicon from $url: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
