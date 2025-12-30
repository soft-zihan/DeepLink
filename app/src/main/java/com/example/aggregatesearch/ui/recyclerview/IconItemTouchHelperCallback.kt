package com.example.aggregatesearch.ui.recyclerview

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aggregatesearch.adapters.IconAdapter
import java.util.Collections

class IconItemTouchHelperCallback(
    private val onDragFinished: (List<com.example.aggregatesearch.data.SearchUrl>) -> Unit
) : ItemTouchHelper.Callback() {

    // A temporary list to hold the reordered items during a drag operation.
    private var tempList: MutableList<com.example.aggregatesearch.data.SearchUrl>? = null

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val adapter = recyclerView.adapter as IconAdapter
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition

        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false
        }

        // On the first move, create a mutable copy of the adapter's list.
        if (tempList == null) {
            tempList = adapter.currentList.toMutableList()
        }

        // Swap the items in our temporary list.
        Collections.swap(tempList, fromPosition, toPosition)

        // Notify the adapter that an item has moved. This will trigger the move animation.
        // Crucially, we do NOT call submitList here, as it would interfere with the drag operation.
        adapter.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe action
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // When the drag is finished, if we have a modified list, pass it to the callback.
        tempList?.let {
            // The onDragFinished callback is responsible for notifying the ViewModel.
            // The ViewModel will then update the database, and the UI will be updated
            // automatically via the Flow. We should not submit the list here directly
            // to maintain a single source of truth.
            onDragFinished(it)
        }

        // Clean up the temporary list for the next drag operation.
        tempList = null
    }

    override fun isLongPressDragEnabled(): Boolean {
        return false // We use manual drag start
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }
}
