package com.example.aggregatesearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.data.SearchUrl

class SearchUrlAdapter(
    private val onItemClicked: (SearchUrl) -> Unit,
    private val onDeleteClicked: (SearchUrl) -> Unit,
    private val onCheckChanged: (SearchUrl, Boolean) -> Unit,
    private val onDragInitiated: (RecyclerView.ViewHolder) -> Unit,
    val onItemMoveRequested: (fromPosition: Int, toPosition: Int) -> Unit
) : ListAdapter<SearchUrl, SearchUrlAdapter.SearchUrlViewHolder>(SEARCH_URL_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchUrlViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_url, parent, false)
        return SearchUrlViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchUrlViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current, onDragInitiated)
    }

    inner class SearchUrlViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUrlName: TextView = itemView.findViewById(R.id.textViewUrlName)
        private val textViewUrlPattern: TextView = itemView.findViewById(R.id.textViewUrlPattern)
        private val buttonDeleteUrl: ImageButton = itemView.findViewById(R.id.buttonDeleteUrl)
        private val checkboxUrlEnabled: CheckBox = itemView.findViewById(R.id.checkboxUrlEnabled)

        fun bind(searchUrl: SearchUrl, dragInitiator: (RecyclerView.ViewHolder) -> Unit) {
            textViewUrlName.text = searchUrl.name
            textViewUrlPattern.text = searchUrl.urlPattern

            checkboxUrlEnabled.setOnCheckedChangeListener(null)
            checkboxUrlEnabled.isChecked = searchUrl.isEnabled
            checkboxUrlEnabled.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(searchUrl, isChecked)
            }

            buttonDeleteUrl.setOnClickListener {
                onDeleteClicked(searchUrl)
            }

            itemView.setOnClickListener {
                onItemClicked(searchUrl)
            }

            itemView.setOnLongClickListener {
                dragInitiator(this)
                true
            }
        }
    }

    companion object {
        private val SEARCH_URL_COMPARATOR = object : DiffUtil.ItemCallback<SearchUrl>() {
            override fun areItemsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SearchUrl, newItem: SearchUrl): Boolean {
                return oldItem == newItem
            }
        }
    }
}
