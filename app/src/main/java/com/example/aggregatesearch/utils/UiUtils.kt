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
import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import android.widget.Button
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.util.Log

object UiUtils {
    private const val DEFAULT_COLOR = "#6200EE"

    /**
     * 应用顶栏颜色到Activity
     */
    fun applyToolbarColor(activity: Activity) {
        val colorInt = getToolbarColor(activity)

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

        // 应用颜色到所有按钮
        applyColorToAllButtons(activity)
    }

    /**
     * 应用主题颜色到活动中的所有按钮
     * 这确保所有界面中的按钮颜色保持一致
     */
    private fun applyColorToAllButtons(activity: Activity) {
        try {
            val colorInt = getToolbarColor(activity)
            val colorStateList = ColorStateList.valueOf(colorInt)

            // 获取活动的根视图
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
            if (rootView != null) {
                // 递归应用颜色到所有按钮
                applyColorToViewGroup(rootView, colorStateList, colorInt)
            }
        } catch (e: Exception) {
            Log.e("UiUtils", "应用颜色到按钮时出错: ${e.message}")
        }
    }

    /**
     * 递归应用颜色到视图组中的所有按钮
     */
    private fun applyColorToViewGroup(view: View, colorStateList: ColorStateList, colorInt: Int) {
        // 为按钮应用颜色
        when (view) {
            is MaterialButton -> {
                if (!view.hasOwnTextColor()) {
                    view.setTextColor(getContrastColor(colorInt))
                }
                // 只修改有背景的按钮
                if (view.backgroundTintList != null) {
                    view.backgroundTintList = colorStateList
                }
            }
            is Button -> {
                if (!view.hasOwnTextColor()) {
                    view.setTextColor(getContrastColor(colorInt))
                }
                // 只修改有背景的按钮
                if (view.backgroundTintList != null) {
                    view.backgroundTintList = colorStateList
                }
            }
        }

        // 递归处理子视图
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyColorToViewGroup(view.getChildAt(i), colorStateList, colorInt)
            }
        }
    }

    /**
     * 判断按钮是否已自定义文本颜色
     */
    private fun Button.hasOwnTextColor(): Boolean {
        return textColors != null && textColors.isStateful
    }

    /**
     * 根据背景色获取对比色（确保文字可读性）
     */
    private fun getContrastColor(backgroundColor: Int): Int {
        return if (isColorLight(backgroundColor)) Color.BLACK else Color.WHITE
    }

    /**
     * 获取用户设置的顶栏颜色
     * 返回 顶栏颜色值
     */
    fun getToolbarColor(context: Context): Int {
        val colorStr = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .getString("toolbar_color", DEFAULT_COLOR) ?: DEFAULT_COLOR
        return try {
            Color.parseColor(colorStr)
        } catch (e: Exception) {
            Color.parseColor(DEFAULT_COLOR)
        }
    }

    /**
     * 获取顶栏颜色字符串
     * 返回 颜色字符串，例如"#6200EE"
     */
    fun getToolbarColorString(context: Context): String {
        return context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .getString("toolbar_color", DEFAULT_COLOR) ?: DEFAULT_COLOR
    }

    /**
     * 判断颜色是否为浅色
     * 返回 true如果是浅色，false如果是深色
     */
    fun isColorLight(color: Int): Boolean {
        return ColorUtils.calculateLuminance(color) > 0.5
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
