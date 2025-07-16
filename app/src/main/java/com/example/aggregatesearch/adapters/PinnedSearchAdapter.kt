package com.example.aggregatesearch.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.R

class PinnedSearchAdapter(
    private val pinnedWords: MutableList<String>,
    private val onWordClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<PinnedSearchAdapter.ViewHolder>() {

    private var isEditMode = false

    fun setEditMode(isEdit: Boolean) {
        isEditMode = isEdit
        notifyDataSetChanged()
    }

    fun getEditMode(): Boolean {
        return isEditMode
    }

    fun setWords(words: List<String>) {
        pinnedWords.clear()
        pinnedWords.addAll(words)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pinned_word, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val word = pinnedWords[position]
        holder.bind(word, isEditMode)
    }

    override fun getItemCount(): Int = pinnedWords.size

    fun removeWord(word: String) {
        val position = pinnedWords.indexOf(word)
        if (position != -1) {
            pinnedWords.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, pinnedWords.size)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.pinned_word_text)
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)

        fun bind(word: String, isEditMode: Boolean) {
            textView.text = word
            deleteButton.visibility = if (isEditMode) View.VISIBLE else View.GONE

            deleteButton.setOnClickListener {
                onDeleteClick(word)
            }

            itemView.setOnClickListener {
                if (!isEditMode) {
                    onWordClick(word)
                }
            }

            itemView.setOnLongClickListener(null)
        }
    }
}
