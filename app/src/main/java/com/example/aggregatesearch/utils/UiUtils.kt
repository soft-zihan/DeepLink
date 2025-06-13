package com.example.aggregatesearch.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager

object UiUtils {
    private const val DEFAULT_COLOR = "#6200EE"

    fun applyToolbarColor(activity: Activity) {
        val colorStr = activity.getSharedPreferences("ui_prefs", 0)
            .getString("toolbar_color", DEFAULT_COLOR) ?: DEFAULT_COLOR
        val colorInt = Color.parseColor(colorStr)

        // 状态栏
        activity.window.statusBarColor = colorInt

        if (activity is AppCompatActivity) {
            activity.supportActionBar?.setBackgroundDrawable(ColorDrawable(colorInt))
        }

        // 自动调整状态栏图标颜色（浅色背景 -> 深色图标，深色背景 -> 浅色图标）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = activity.window.decorView
            // 计算颜色亮度
            val isColorLight = ColorUtils.calculateLuminance(colorInt) > 0.5
            if (isColorLight) {
                // 设置浅色状态栏，图标变深
                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                // 清除浅色状态栏标记，图标变浅
                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
    }

    /**
     * 根据当前主题为根视图设置壁纸
     */
    fun applyWallpaper(root: View, context: Context) {
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val uriString = if (isDark) prefs.getString("wallpaper_dark_uri", null) else prefs.getString("wallpaper_light_uri", null)
        if (uriString.isNullOrEmpty()) {
            root.background = null
            return
        }
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                val drawable = BitmapDrawable(context.resources, bitmap)
                val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
                val alphaPercent = defaultPrefs.getInt(
                    if (isDark) "pref_wallpaper_dark_alpha" else "pref_wallpaper_light_alpha", 100)
                drawable.alpha = (alphaPercent * 255 / 100).coerceIn(0, 255)
                root.background = drawable
            }
        } catch (e: Exception) {
            root.background = null
        }
    }
} 