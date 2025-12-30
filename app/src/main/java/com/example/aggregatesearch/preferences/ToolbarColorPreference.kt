package com.example.aggregatesearch.preferences

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.widget.GridLayout
import com.google.android.material.card.MaterialCardView

/**
 * 自定义顶栏颜色首选项，提供颜色选择功能
 */
class ToolbarColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var selectedColorView: View? = null
    private var currentColor: String = "#6200EE" // 默认颜色值

    init {
        layoutResource = R.layout.pref_toolbar_color_picker
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        selectedColorView = holder.findViewById(R.id.selected_color_view)
        val colorPickerButton = holder.findViewById(R.id.btn_pick_color)

        val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        currentColor = prefs.getString("toolbar_color", "#6200EE") ?: "#6200EE"
        selectedColorView?.setBackgroundColor(Color.parseColor(currentColor))

        colorPickerButton.setOnClickListener {
            showColorPickerDialog(currentColor)
        }
    }

    /**
     * 显示颜色选择对话框
     */
    private fun showColorPickerDialog(currentColorHex: String) {
        // 预定义的颜色数组
        val colors = arrayOf(
            "#F44336", // 红色
            "#E91E63", // 粉红色
            "#9C27B0", // 紫色
            "#673AB7", // 深紫色
            "#3F51B5", // 靛蓝色
            "#2196F3", // 蓝色
            "#03A9F4", // 浅蓝色
            "#00BCD4", // 青色
            "#009688", // 茶绿色
            "#4CAF50", // 绿色
            "#8BC34A", // 浅绿色
            "#CDDC39", // 青柠色
            "#FFEB3B", // 黄色
            "#FFC107", // 琥珀色
            "#FF9800", // 橙色
            "#FF5722", // 深橙色
            "#795548", // 棕色
            "#9E9E9E", // 灰色
            "#607D8B", // 蓝灰色
            "#000000", // 黑色
            "#FFFFFF"  // 白色
        )

        val builder = AlertDialog.Builder(context)
        builder.setTitle("选择顶栏颜色")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val gridLayout = view.findViewById<GridLayout>(R.id.colorGrid)

        // 动态添加颜色选项
        for (colorHex in colors) {
            val colorView = MaterialCardView(context)
            val size = 48
            val margin = 4

            val params = GridLayout.LayoutParams()
            params.width = dpToPx(size)
            params.height = dpToPx(size)
            params.setMargins(dpToPx(margin), dpToPx(margin), dpToPx(margin), dpToPx(margin))

            colorView.layoutParams = params
            colorView.cardElevation = 2f
            colorView.radius = dpToPx(size) / 2f

            val color = Color.parseColor(colorHex)
            colorView.setCardBackgroundColor(color)

            colorView.setOnClickListener {
                onColorSelected(color, colorHex)
                dialog?.dismiss()
            }

            gridLayout.addView(colorView)
        }

        builder.setView(view)
        builder.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }

        dialog = builder.create()
        dialog?.show()
    }

    /**
     * 当颜色被选择时的回调
     */
    private fun onColorSelected(color: Int, colorHex: String) {
        currentColor = colorHex
        selectedColorView?.setBackgroundColor(color)

        // 保存选择的颜色到共享首选项
        val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("toolbar_color", colorHex)
        }

        // 重新创建活动以应用主题更改
        (context as? Activity)?.recreate()
    }

    private fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private var dialog: AlertDialog? = null
}
