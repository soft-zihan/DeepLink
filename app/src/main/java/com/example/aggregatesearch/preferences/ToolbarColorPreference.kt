package com.example.aggregatesearch.preferences

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R
import com.google.android.material.button.MaterialButtonToggleGroup

class ToolbarColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private val purple = "#6200EE"
    private val blue = "#03A9F4"
    private val green = "#4CAF50"
    private val red = "#F44336"
    private val black = "#000000"

    init {
        layoutResource = R.layout.pref_toolbar_color
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val group = holder.findViewById(R.id.colorToggle) as MaterialButtonToggleGroup
        val btnPurple = holder.findViewById(R.id.btnPurple)
        val btnBlue = holder.findViewById(R.id.btnBlue)
        val btnGreen = holder.findViewById(R.id.btnGreen)
        val btnRed = holder.findViewById(R.id.btnRed)
        val btnBlack = holder.findViewById(R.id.btnBlack)

        val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val current = prefs.getString("toolbar_color", purple) ?: purple
        when (current) {
            purple -> group.check(btnPurple.id)
            blue -> group.check(btnBlue.id)
            green -> group.check(btnGreen.id)
            red -> group.check(btnRed.id)
            else -> group.check(btnBlack.id)
        }

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val colorStr = when (checkedId) {
                btnPurple.id -> purple
                btnBlue.id -> blue
                btnGreen.id -> green
                btnRed.id -> red
                else -> black
            }
            prefs.edit().putString("toolbar_color", colorStr).apply()
            (context as? Activity)?.recreate()
        }
    }
} 