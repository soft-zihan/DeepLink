package com.example.aggregatesearch.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.appcompat.app.AppCompatDelegate
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.GridLayout
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.setMargins

class ThemeAndColorPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var currentColor: Int = Color.BLUE
    private var currentTheme: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    private var sharedPrefs: SharedPreferences
    private lateinit var colorPreview: View
    private lateinit var themeToggleGroup: MaterialButtonToggleGroup

    init {
        widgetLayoutResource = R.layout.pref_theme_and_color_widget
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        // 加载保存的颜色和主题设置
        val uiPrefs = context.getSharedPreferences("ui_prefs", 0)
        // 从字符串格式加载颜色值，如果没有则使用默认颜色
        val colorStr = uiPrefs.getString("toolbar_color", "#6200EE") ?: "#6200EE"
        try {
            currentColor = Color.parseColor(colorStr)
        } catch (e: Exception) {
            currentColor = context.getColor(R.color.purple_500)
        }

        currentTheme = sharedPrefs.getInt("pref_theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // 初始化UI组件 - 使用正确的类型转换方式
        colorPreview = holder.findViewById(R.id.color_preview)
        themeToggleGroup = holder.findViewById(R.id.theme_toggle_group) as MaterialButtonToggleGroup

        // 设置颜色预览
        colorPreview.backgroundTintList = ColorStateList.valueOf(currentColor)

        // 设置主题切换按钮
        setupThemeButtons()

        // 设置颜色选择器点击事件
        val colorPickerContainer = holder.findViewById(R.id.color_picker_container) as RelativeLayout
        colorPickerContainer.setOnClickListener {
            ColorPickerDialog(context, currentColor).show()
        }

        // 为整个偏好项添加点击事件，确保点击任何区域都能响应
        holder.itemView.setOnClickListener {
            ColorPickerDialog(context, currentColor).show()
        }
    }

    private fun setupThemeButtons() {
        // 根据保存的主题设置切换按钮状态
        when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> themeToggleGroup.check(R.id.btnLight)
            AppCompatDelegate.MODE_NIGHT_YES -> themeToggleGroup.check(R.id.btnDark)
            else -> themeToggleGroup.check(R.id.btnSystem)
        }

        // 设置主题切换监听器
        themeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnLight -> setThemeMode(AppCompatDelegate.MODE_NIGHT_NO)
                    R.id.btnDark -> setThemeMode(AppCompatDelegate.MODE_NIGHT_YES)
                    R.id.btnSystem -> setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
    }

    // 设置颜色
    private fun setColor(color: Int) {
        currentColor = color
        colorPreview.backgroundTintList = ColorStateList.valueOf(color)

        // 保存选择的颜色为十六进制字符串格式
        val colorHex = String.format("#%06X", 0xFFFFFF and color)
        context.getSharedPreferences("ui_prefs", 0).edit {
            putString("toolbar_color", colorHex)
        }
    }

    // 设置主题模式
    private fun setThemeMode(mode: Int) {
        if (currentTheme == mode) return

        currentTheme = mode
        sharedPrefs.edit {
            putInt("pref_theme_mode", mode)
        }

        // 立即应用新主题
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // 颜色选择器对话框
    inner class ColorPickerDialog(context: Context, initialColor: Int) {
        private var dialog: AlertDialog = AlertDialog.Builder(context).create()

        // 预定义颜色列表
        private val colors = intArrayOf(
            "#F44336".toColorInt(), // 红色
            "#E91E63".toColorInt(), // 粉红色
            "#9C27B0".toColorInt(), // 紫色
            "#673AB7".toColorInt(), // 深紫色
            "#3F51B5".toColorInt(), // 靛蓝色
            "#2196F3".toColorInt(), // 蓝色
            "#03A9F4".toColorInt(), // 浅蓝色
            "#00BCD4".toColorInt(), // 青色
            "#009688".toColorInt(), // 蓝绿色
            "#4CAF50".toColorInt(), // 绿色
            "#8BC34A".toColorInt(), // 浅绿色
            "#CDDC39".toColorInt(), // 酸橙色
            "#FFEB3B".toColorInt(), // 黄色
            "#FFC107".toColorInt(), // 琥珀色
            "#FF9800".toColorInt(), // 橙色
            "#FF5722".toColorInt(), // 深橙色
            "#795548".toColorInt(), // 棕色
            "#9E9E9E".toColorInt(), // 灰色
            "#607D8B".toColorInt(), // 蓝灰色
            "#000000".toColorInt()  // 黑色
        )

        init {
            // 创建对话框构建器
            val builder = AlertDialog.Builder(context)
            builder.setTitle("选择颜色")

            // 创建颜色网格布局
            val gridLayout = GridLayout(context)
            gridLayout.columnCount = 5
            gridLayout.setPadding(16, 16, 16, 16)

            // 添加颜色按钮到网格
            for (color in colors) {
                val button = Button(context)
                val params = GridLayout.LayoutParams()
                params.setMargins(8)
                params.width = 60
                params.height = 60
                button.layoutParams = params

                // 设置按钮背景为圆形色块
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                drawable.color = ColorStateList.valueOf(color)
                button.background = drawable

                // 设置点击事件
                button.setOnClickListener {
                    setColor(color)
                    dialog.dismiss()
                }

                gridLayout.addView(button)
            }

            // 构建并显示对话框
            builder.setView(gridLayout)
            builder.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            dialog = builder.create()
        }

        fun show() {
            dialog.show()
        }
    }
}
