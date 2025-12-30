package com.example.aggregatesearch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.utils.IconLoader

class IconAdapter(
    private val onIconStateChanged: (SearchUrl) -> Unit,
    private val onIconClick: (SearchUrl) -> Unit,
    private val onEditIconClick: (SearchUrl) -> Unit,
    private val itemTouchHelperProvider: () -> ItemTouchHelper
) : ListAdapter<SearchUrl, IconAdapter.IconViewHolder>(IconDiffCallback()) {

    private var isEditMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_url_icon, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val searchUrl = getItem(position)
        holder.bind(searchUrl, isEditMode)
    }

    fun setEditMode(editMode: Boolean) {
        if (isEditMode != editMode) {
            isEditMode = editMode
            notifyDataSetChanged()
        }
    }

    inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewIcon: ImageView = itemView.findViewById(R.id.imageViewIcon)
        private val textViewUrlName: TextView = itemView.findViewById(R.id.textViewUrlName)
        private val iconEditButton: ImageView = itemView.findViewById(R.id.iconEditButton)

        fun bind(searchUrl: SearchUrl, isEditMode: Boolean) {
            textViewUrlName.text = searchUrl.name
            IconLoader.loadIcon(itemView.context, searchUrl, imageViewIcon)

            iconEditButton.visibility = if (isEditMode) View.VISIBLE else View.GONE
            iconEditButton.setOnClickListener { onEditIconClick(searchUrl) }

            itemView.setOnClickListener {
                // Immediately update the UI
                searchUrl.isEnabled = !searchUrl.isEnabled
                updateIconVisualState(imageViewIcon, searchUrl.isEnabled)

                // Notify for background data update
                onIconStateChanged(searchUrl)

                // Handle the original click action (e.g., for double-click)
                onIconClick(searchUrl)
            }

            itemView.setOnLongClickListener {
                // Icon dragging is not restricted to edit mode
                itemTouchHelperProvider().startDrag(this)
                true
            }

            // Apply visual state based on the provided state map
            updateIconVisualState(imageViewIcon, searchUrl.isEnabled)
        }

        fun updateIconVisualState(imageView: ImageView, isSelected: Boolean) {
            imageView.isSelected = isSelected
            if (isSelected) {
                imageView.alpha = 1.0f
                imageView.scaleX = 1.15f
                imageView.scaleY = 1.15f
                imageView.background = androidx.core.content.ContextCompat.getDrawable(
                    imageView.context, R.drawable.selected_icon_background
                )
            } else {
                imageView.alpha = 0.7f
                imageView.scaleX = 1.0f
                imageView.scaleY = 1.0f
                imageView.background = null
            }
        }
    }
}

class IconDiffCallback : DiffUtil.ItemCallback<SearchUrl>() {
    override fun areItemsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
        return oldItem == newItem
    }
}
