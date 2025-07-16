package com.example.aggregatesearch.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R
import com.example.aggregatesearch.utils.ThemeHelper
import com.google.android.material.button.MaterialButtonToggleGroup
import android.os.Handler
import android.os.Looper

/**
 * 自定义主题选择偏好设置
 * 以三选一按钮组的形式展示浅色/深色/系统主题选项
 */
class ThemePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var themeHelper: ThemeHelper = ThemeHelper(context)

    init {
        layoutResource = R.layout.preference_widget_frame
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // 获取FrameLayout容器
        val preferenceFrame = holder.itemView as FrameLayout
        preferenceFrame.removeAllViews()

        // 膨胀主题选择器布局
        val customLayout = LayoutInflater.from(context).inflate(R.layout.pref_theme_selector, preferenceFrame, false)
        preferenceFrame.addView(customLayout)

        // 获取主题切换按钮组
        val toggleGroup = customLayout.findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)

        // 根据当前主题设置选中状态
        when (themeHelper.getThemeMode()) {
            ThemeHelper.MODE_LIGHT -> toggleGroup.check(R.id.button_light_theme)
            ThemeHelper.MODE_DARK -> toggleGroup.check(R.id.button_dark_theme)
            ThemeHelper.MODE_SYSTEM -> toggleGroup.check(R.id.button_system_theme)
        }

        // 设置切换监听器
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newThemeMode = when (checkedId) {
                    R.id.button_light_theme -> ThemeHelper.MODE_LIGHT
                    R.id.button_dark_theme -> ThemeHelper.MODE_DARK
                    R.id.button_system_theme -> ThemeHelper.MODE_SYSTEM
                    else -> ThemeHelper.MODE_SYSTEM
                }

                if (newThemeMode != themeHelper.getThemeMode()) {
                    // 保存新的主题设置
                    themeHelper.setThemeMode(newThemeMode)

                    // 通知主题更改
                    callChangeListener(newThemeMode)

                    // 显示提示信息
                    val themeNames = arrayOf("浅色", "深色", "跟随系统")
                    Toast.makeText(context, "已切换到${themeNames[newThemeMode]}主题", Toast.LENGTH_SHORT).show()

                    // 使用更安全的方式重建Activity以应用新主题
                    val activity = context as? Activity
                    activity?.let {
                        try {
                            // 延迟执行重建，避免ActionBar冲突
                            Handler(Looper.getMainLooper()).postDelayed({
                                // 使用特定的标志重新创建活动以避免ActionBar重复初始化问题
                                val intent = it.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                it.finish()
                                it.startActivity(intent)
                                it.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            }, 100)
                        } catch (e: Exception) {
                            // 出现异常时的备用方案
                            Handler(Looper.getMainLooper()).postDelayed({
                                ThemeHelper.applyTheme(newThemeMode)
                            }, 100)
                        }
                    }
                }
            }
        }
    }
}
