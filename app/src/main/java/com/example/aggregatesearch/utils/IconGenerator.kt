package com.example.aggregatesearch.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * 图标生成器，用于创建文字图标
 */
object IconGenerator {

    /**
     * 生成文字图标
     * @param context 上下文
     * @param textIcon 文字图标数据
     * @return 生成的图标位图
     */
    fun generateIcon(context: Context, textIcon: TextIcon): Bitmap {
        val iconSize = 128 // 图标大小，单位像素
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 设置背景
        val backgroundPaint = Paint().apply {
            color = textIcon.backgroundColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, iconSize.toFloat(), iconSize.toFloat(), backgroundPaint)

        // 如果文本为空，则返回纯色背景
        if (textIcon.text.isEmpty()) {
            return bitmap
        }

        // 设置文字
        val textPaint = Paint().apply {
            color = Color.WHITE // 文字颜色为白色
            textSize = if (textIcon.text.length > 1) iconSize / 3f else iconSize / 2f // 根据字符长度调整文字大小
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // 计算文字位置，使其居中
        val textBounds = Rect()
        textPaint.getTextBounds(textIcon.text, 0, textIcon.text.length, textBounds)
        val x = iconSize / 2f
        val y = iconSize / 2f - textBounds.exactCenterY()

        // 绘制文字
        canvas.drawText(textIcon.text, x, y, textPaint)

        return bitmap
    }
}
