package com.example.aggregatesearch.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R
import com.google.android.material.button.MaterialButtonToggleGroup

class ThemeTogglePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_theme_toggle
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val group = holder.findViewById(R.id.toggleGroup) as MaterialButtonToggleGroup
        val btnLight = holder.findViewById(R.id.btnLight) as View
        val btnDark = holder.findViewById(R.id.btnDark) as View
        val btnSystem = holder.findViewById(R.id.btnSystem) as View

        // 读取当前模式设置按钮选中
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> group.check(btnLight.id)
            AppCompatDelegate.MODE_NIGHT_YES -> group.check(btnDark.id)
            else -> group.check(btnSystem.id)
        }

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                btnLight.id -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                btnDark.id -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                btnSystem.id -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            (context as? android.app.Activity)?.recreate()
        }
    }
} 