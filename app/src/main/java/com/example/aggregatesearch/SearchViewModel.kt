package com.example.aggregatesearch

import android.util.Log
import androidx.lifecycle.*
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.SearchUrlRepository
import com.example.aggregatesearch.data.UrlGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: SearchUrlRepository) : ViewModel() {

    private val TAG = "SearchViewModel"

    private val _allUrls = MutableStateFlow<List<SearchUrl>>(emptyList())
    val allUrls: StateFlow<List<SearchUrl>> = _allUrls.asStateFlow()

    private val _allGroups = MutableStateFlow<List<UrlGroup>>(emptyList())
    val allGroups: StateFlow<List<UrlGroup>> = _allGroups.asStateFlow()

    val groupedItems: StateFlow<List<UrlGroup>> = _allGroups
        .combine(_allUrls) { groups, urls ->
            groups.map { group ->
                val newGroup = group.copy()
                newGroup.urls = urls.filter { it.groupId == group.id }.sortedBy { it.orderIndex }
                newGroup
            }.sortedBy { it.orderIndex }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            // Ensure the database is initialized before loading data
            repository.initializeDatabaseIfFirstLaunch()

            // Load initial data from the repository.
            // After this, the StateFlows in the ViewModel are the single source of truth for the UI.
            // Database updates become "fire and forget" to prevent UI state rebound.
            _allUrls.value = repository.allUrls.first()
            _allGroups.value = repository.allGroups.first()
        }
    }

    fun insert(searchUrl: SearchUrl) = viewModelScope.launch {
        try {
            val maxOrderIndex = _allUrls.value.filter { it.groupId == searchUrl.groupId }.mapNotNull { it.orderIndex }.maxOrNull() ?: -1
            val newUrl = searchUrl.copy(orderIndex = maxOrderIndex + 1)
            val newId = repository.insert(newUrl)
            val finalUrl = newUrl.copy(id = newId)
            _allUrls.update { it + finalUrl }
            Log.d(TAG, "Inserted URL: ${finalUrl.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting URL: ${searchUrl.name}", e)
        }
    }

    fun updateUrl(searchUrl: SearchUrl) {
        // Optimistic update
        _allUrls.update { currentUrls ->
            currentUrls.map { if (it.id == searchUrl.id) searchUrl else it }
        }

        // Database update
        viewModelScope.launch {
            try {
                repository.update(searchUrl)
                Log.d(TAG, "Updated URL: ${searchUrl.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating URL: ${searchUrl.name}", e)
            }
        }
    }

    fun updateUrlStateInBackground(searchUrl: SearchUrl) {
        // Optimistic update to ensure StateFlow is the source of truth.
        _allUrls.update { currentUrls ->
            currentUrls.map { if (it.id == searchUrl.id) searchUrl else it }
        }

        viewModelScope.launch {
            try {
                repository.update(searchUrl)
                Log.d(TAG, "Updated URL state in background: ${searchUrl.name}, isEnabled: ${searchUrl.isEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating URL state in background: ${searchUrl.name}", e)
            }
        }
    }

    fun delete(searchUrl: SearchUrl) {
        // Optimistic update
        _allUrls.update { currentUrls ->
            currentUrls.filterNot { it.id == searchUrl.id }
        }

        // Database update
        viewModelScope.launch {
            try {
                repository.delete(searchUrl)
                Log.d(TAG, "Deleted URL: ${searchUrl.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting URL: ${searchUrl.name}", e)
            }
        }
    }

    fun updateUrlsOrder(orderedUrls: List<SearchUrl>, targetGroupId: Long? = null) {
        if (orderedUrls.isEmpty()) return

        val finalListToUpdate = orderedUrls.mapIndexed { index, url ->
            url.copy(orderIndex = index, groupId = targetGroupId ?: url.groupId)
        }

        // Optimistic update
        _allUrls.update { currentUrls ->
            val updatedIds = finalListToUpdate.map { it.id }.toSet()
            val otherUrls = currentUrls.filterNot { it.id in updatedIds }
            otherUrls + finalListToUpdate
        }

        // Database update
        viewModelScope.launch {
            try {
                repository.updateAllUrls(finalListToUpdate)
                Log.d(TAG, "Updated order for ${finalListToUpdate.size} URLs")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating URLs order", e)
                // Revert on error - this is complex, for now, we log the error
                // A full revert would require storing the pre-update state.
            }
        }
    }

    fun addGroup(groupName: String) = viewModelScope.launch {
        try {
            if (groupName.isBlank()) {
                Log.w(TAG, "Attempted to add group with blank name")
                return@launch
            }

            val maxOrderIndex = _allGroups.value.mapNotNull { it.orderIndex }.maxOrNull() ?: -1
            val newGroup = UrlGroup(name = groupName, orderIndex = maxOrderIndex + 1)
            val newId = repository.insertGroup(newGroup)
            val finalGroup = newGroup.copy(id = newId)
            _allGroups.update { it + finalGroup }
            Log.d(TAG, "Added new group: $groupName")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding group: $groupName", e)
        }
    }

    fun deleteGroup(group: UrlGroup) = viewModelScope.launch {
        try {
            repository.deleteGroup(group)
            Log.d(TAG, "Deleted group: ${group.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting group: ${group.name}", e)
        }
    }

    fun updateGroupExpanded(group: UrlGroup, isExpanded: Boolean) {
        val updatedGroup = group.copy(isExpanded = isExpanded)
        _allGroups.update { currentGroups ->
            currentGroups.map { if (it.id == updatedGroup.id) updatedGroup else it }
        }

        viewModelScope.launch {
            try {
                repository.updateGroup(updatedGroup)
                Log.d(TAG, "Updated group expansion in background: ${updatedGroup.name}, expanded: $isExpanded")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating group expansion: ${updatedGroup.name}", e)
            }
        }
    }

    fun updateGroupSelected(group: UrlGroup, isSelected: Boolean) {
        val urlsToUpdate = _allUrls.value
            .filter { it.groupId == group.id }
            .map { it.copy(isEnabled = isSelected) }

        if (urlsToUpdate.isEmpty()) {
            return
        }

        val groupUrlIds = urlsToUpdate.map { it.id }.toSet()
        _allUrls.update { currentUrls ->
            currentUrls.filterNot { it.id in groupUrlIds } + urlsToUpdate
        }

        viewModelScope.launch {
            try {
                repository.updateAllUrls(urlsToUpdate)
                Log.d(TAG, "Updated group selection in background: ${group.name}, selected: $isSelected")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating group selection: ${group.name}", e)
            }
        }
    }

    fun updateGroupsOrder(orderedGroups: List<UrlGroup>) {
        val updatedGroups = orderedGroups.mapIndexed { index, group ->
            group.copy(orderIndex = index)
        }
        if (updatedGroups.isEmpty()) return

        // Optimistic update
        _allGroups.value = updatedGroups

        // Database update
        viewModelScope.launch {
            try {
                repository.updateGroups(updatedGroups)
                Log.d(TAG, "Updated groups order")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating groups order", e)
                // Revert on error
            }
        }
    }

    fun moveUrlToGroup(url: SearchUrl, newGroupId: Long, newOrderInGroup: Int) = viewModelScope.launch {

        val currentAllUrls = allUrls.value

        val oldGroupUrls = currentAllUrls
            .filter { it.groupId == url.groupId && it.id != url.id }
            .sortedBy { it.orderIndex }
            .mapIndexed { index, searchUrl -> searchUrl.copy(orderIndex = index) }

        val newGroupInitialUrls = currentAllUrls
            .filter { it.groupId == newGroupId && it.id != url.id }
            .sortedBy { it.orderIndex }
            .toMutableList()

        val movedUrlFinal = url.copy(groupId = newGroupId, orderIndex = newOrderInGroup)
        if (newOrderInGroup >= newGroupInitialUrls.size) {
            newGroupInitialUrls.add(movedUrlFinal)
        } else {
            newGroupInitialUrls.add(newOrderInGroup, movedUrlFinal)
        }
        val newGroupFinalUrls = newGroupInitialUrls.mapIndexed { index, searchUrl -> searchUrl.copy(orderIndex = index) }

        val urlsToUpdate = mutableListOf<SearchUrl>()
        urlsToUpdate.addAll(oldGroupUrls)
        urlsToUpdate.addAll(newGroupFinalUrls)

        repository.updateAllUrls(urlsToUpdate.distinctBy { it.id })
    }

    fun getUrlsByGroupId(groupId: Long): List<SearchUrl> {
        return allUrls.value.filter { it.groupId == groupId }.sortedBy { it.orderIndex }
    }

    fun updateUrls(urls: List<SearchUrl>) = viewModelScope.launch {
        repository.updateAllUrls(urls)
    }

    fun restoreFromBackup(groups: List<UrlGroup>, urls: List<SearchUrl>) = viewModelScope.launch {
        repository.deleteAllGroups()
        repository.deleteAllUrls()

        groups.forEach { group ->
            repository.insertGroupWithId(group)
        }

        urls.forEach { url ->
            repository.insertUrlWithId(url)
        }
    }

    fun updateGroup(group: UrlGroup) {
        // Optimistic update
        _allGroups.update { currentGroups ->
            currentGroups.map { if (it.id == group.id) group else it }
        }

        // Database update
        viewModelScope.launch {
            repository.updateGroup(group)
        }
    }

    fun updateIconOrderInGroup(orderedUrls: List<SearchUrl>) {
        if (orderedUrls.isEmpty()) return

        val urlsToUpdate = orderedUrls.mapIndexed { index, url ->
            url.copy(orderIndex = index)
        }

        // Optimistic update
        val groupId = urlsToUpdate.firstOrNull()?.groupId
        if (groupId != null) {
            _allUrls.update { currentUrls ->
                // Remove old versions of the reordered URLs and add the new versions.
                val otherGroupUrls = currentUrls.filter { it.groupId != groupId }
                otherGroupUrls + urlsToUpdate
            }
        }

        // Database update
        viewModelScope.launch {
            try {
                repository.updateAllUrls(urlsToUpdate)
                Log.d(TAG, "Updated icon order in background for group $groupId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating icon order in group", e)
            }
        }
    }

    // 获取所有启用的URL
    fun getEnabledUrls(): List<SearchUrl> {
        return allUrls.value.filter { it.isEnabled }
    }

    // 设置所有URL的选中状态
    fun setAllSelected(selected: Boolean) {
        val updatedUrls = _allUrls.value.map { it.copy(isEnabled = selected) }
        _allUrls.value = updatedUrls

        viewModelScope.launch {
            try {
                repository.updateAllUrls(updatedUrls)
                Log.d(TAG, "Set all URLs selected in background: $selected")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting all URLs selected: $selected", e)
            }
        }
    }


    private val _groupCheckEvents = MutableLiveData<Pair<UrlGroup, Boolean>>()
    val groupCheckEvents: LiveData<Pair<UrlGroup, Boolean>> = _groupCheckEvents
}

class SearchViewModelFactory(private val repository: SearchUrlRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
