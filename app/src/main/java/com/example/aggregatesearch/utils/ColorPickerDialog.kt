package com.example.aggregatesearch.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.example.aggregatesearch.R
import com.example.aggregatesearch.databinding.DialogColorPickerBinding

/**
 * 颜色选择器对话框
 * 用于替代第三方库，实现简单的颜色选择功能
 */
class ColorPickerDialog private constructor(
    context: Context,
    private val title: String,
    private val defaultColor: Int,
    private val isCircleShape: Boolean,
    private val colorListener: (color: Int, hexColor: String) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogColorPickerBinding
    private var selectedColor: Int = defaultColor

    companion object {
        // 预定义颜色
        private val PRESET_COLORS = arrayOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
            "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E",
            "#607D8B", "#000000", "#FFFFFF"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogColorPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置标题
        binding.textViewTitle.text = title

        // 显示当前颜色
        updateColorPreview(defaultColor)

        // 创建颜色网格
        createColorGrid()

        // 设置自定义颜色输入
        setupCustomColorInput()

        // 设置按钮
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonConfirm.setOnClickListener {
            colorListener(selectedColor, String.format("#%06X", 0xFFFFFF and selectedColor))
            dismiss()
        }
    }

    private fun createColorGrid() {
        val gridLayout = binding.colorGrid
        gridLayout.removeAllViews()

        for ((index, colorHex) in PRESET_COLORS.withIndex()) {
            try {
                val color = colorHex.toColorInt()
                val colorView = View(context).apply {
                    setBackgroundColor(color)
                    if (isCircleShape) {
                        background = context.getDrawable(R.drawable.circle_color)?.apply {
                            setTint(color)
                        }
                    }
                }

                // 添加点击事件
                colorView.setOnClickListener {
                    selectedColor = color
                    updateColorPreview(color)
                    binding.editTextHexColor.setText(String.format("#%06X", 0xFFFFFF and color))
                }

                // 添加到网格
                val params = GridLayout.LayoutParams()
                params.width = 80
                params.height = 80
                params.rowSpec = GridLayout.spec(index / 5)
                params.columnSpec = GridLayout.spec(index % 5)
                params.setMargins(8, 8, 8, 8)
                gridLayout.addView(colorView, params)
            } catch (e: Exception) {
                // 颜色解析错误时跳过
            }
        }
    }

    private fun setupCustomColorInput() {
        binding.editTextHexColor.setText(String.format("#%06X", 0xFFFFFF and defaultColor))

        binding.buttonApplyHex.setOnClickListener {
            try {
                val hexColor = binding.editTextHexColor.text.toString()
                val color = if (hexColor.startsWith("#")) {
                    Color.parseColor(hexColor)
                } else {
                    Color.parseColor("#$hexColor")
                }
                selectedColor = color
                updateColorPreview(color)
            } catch (e: Exception) {
                // 颜色解析错误处理
                binding.editTextHexColor.error = "颜色格式无效"
            }
        }
    }

    private fun updateColorPreview(color: Int) {
        binding.viewColorPreview.setBackgroundColor(color)
        // 判断文字颜色是黑色还是白色以确保可见性
        val brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        val textColor = if (brightness < 128) Color.WHITE else Color.BLACK
        binding.textViewHex.setTextColor(textColor)
        binding.textViewHex.text = String.format("#%06X", 0xFFFFFF and color)
    }

    /**
     * 构建器模式，用于创建颜色选择器实例
     */
    class Builder(private val context: Context) {
        private var title: String = "选择颜色"
        private var defaultColor: Int = Color.BLUE
        private var isCircleShape: Boolean = true
        private var colorListener: (color: Int, hexColor: String) -> Unit = { _, _ -> }

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setDefaultColor(defaultColor: Int): Builder {
            this.defaultColor = defaultColor
            return this
        }

        fun setDefaultColor(colorHex: String): Builder {
            try {
                this.defaultColor = colorHex.toColorInt()
            } catch (e: Exception) {
                // 解析失败时使用默认蓝色
                this.defaultColor = Color.BLUE
            }
            return this
        }

        fun setColorShape(isCircle: Boolean): Builder {
            this.isCircleShape = isCircle
            return this
        }

        fun setColorListener(listener: (color: Int, hexColor: String) -> Unit): Builder {
            this.colorListener = listener
            return this
        }

        fun show(): ColorPickerDialog {
            val dialog = ColorPickerDialog(context, title, defaultColor, isCircleShape, colorListener)
            dialog.show()
            return dialog
        }
    }

    /**
     * 颜色形状枚举
     */
    enum class ColorShape {
        CIRCLE, SQUARE
    }
}
