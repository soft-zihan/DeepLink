package com.example.aggregatesearch.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.aggregatesearch.R

class WallpaperPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private var onDeleteClickListener: (() -> Unit)? = null

    init {
        widgetLayoutResource = R.layout.pref_wallpaper
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val deleteButton = holder.findViewById(R.id.btn_delete_wallpaper) as Button
        deleteButton.setOnClickListener {
            onDeleteClickListener?.invoke()
        }
    }

    fun setOnDeleteClickListener(listener: () -> Unit) {
        onDeleteClickListener = listener
    }
}
