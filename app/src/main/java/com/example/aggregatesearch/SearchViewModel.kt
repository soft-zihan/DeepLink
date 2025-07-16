package com.example.aggregatesearch

import android.util.Log
import androidx.lifecycle.*
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.SearchUrlRepository
import com.example.aggregatesearch.data.UrlGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class SearchViewModel(private val repository: SearchUrlRepository) : ViewModel() {

    private val TAG = "SearchViewModel"

    val allUrls: StateFlow<List<SearchUrl>> = repository.allUrls
        .catch { e ->
            Log.e(TAG, "Error collecting URLs", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allGroups: StateFlow<List<UrlGroup>> = repository.allGroups
        .catch { e ->
            Log.e(TAG, "Error collecting groups", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedItems: StateFlow<List<Any>> = combine(allGroups, allUrls) { groups, urls ->
        val items = mutableListOf<Any>()
        try {
            groups.sortedBy { it.orderIndex }.forEach { group ->
                items.add(group)
                if (group.isExpanded) {
                    items.addAll(urls.filter { it.groupId == group.id }.sortedBy { it.orderIndex })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error combining groups and URLs", e)
        }
        items
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(searchUrl: SearchUrl) = viewModelScope.launch {
        try {
            val maxOrderIndex = repository.getMaxOrderIndexForGroup(searchUrl.groupId) ?: -1
            repository.insert(searchUrl.copy(orderIndex = maxOrderIndex + 1))
            Log.d(TAG, "Inserted URL: ${searchUrl.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting URL: ${searchUrl.name}", e)
        }
    }

    fun updateUrl(searchUrl: SearchUrl) = viewModelScope.launch {
        try {
            repository.update(searchUrl)
            Log.d(TAG, "Updated URL: ${searchUrl.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating URL: ${searchUrl.name}", e)
        }
    }

    fun delete(searchUrl: SearchUrl) = viewModelScope.launch {
        try {
            repository.delete(searchUrl)
            Log.d(TAG, "Deleted URL: ${searchUrl.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting URL: ${searchUrl.name}", e)
        }
    }

    fun updateUrlsOrder(orderedUrls: List<SearchUrl>, targetGroupId: Long? = null) = viewModelScope.launch {
        try {
            if (orderedUrls.isEmpty()) return@launch

            val finalListToPersist = orderedUrls.mapIndexed { index, url ->
                url.copy(orderIndex = index, groupId = targetGroupId ?: url.groupId)
            }

            repository.updateAllUrls(finalListToPersist)
            Log.d(TAG, "Updated order for ${finalListToPersist.size} URLs")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating URLs order", e)
        }
    }

    fun addGroup(groupName: String) = viewModelScope.launch {
        try {
            if (groupName.isBlank()) {
                Log.w(TAG, "Attempted to add group with blank name")
                return@launch
            }

            val maxOrderIndex = repository.getMaxGroupOrderIndex() ?: -1
            val newGroup = UrlGroup(name = groupName, orderIndex = maxOrderIndex + 1)
            repository.insertGroup(newGroup)
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

    fun updateGroupExpanded(group: UrlGroup, isExpanded: Boolean) = viewModelScope.launch {
        try {
            repository.updateGroup(group.copy(isExpanded = isExpanded))
            Log.d(TAG, "Updated group expansion: ${group.name}, expanded: $isExpanded")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group expansion: ${group.name}", e)
        }
    }

    fun updateGroupSelected(group: UrlGroup, isSelected: Boolean) = viewModelScope.launch {
        val currentAllUrls = repository.allUrls.first()

        val urlsInGroup = currentAllUrls.filter { it.groupId == group.id }

        if (urlsInGroup.isEmpty()) {
            return@launch
        }

        val updatedUrls = urlsInGroup.map { it.copy(isEnabled = isSelected) }
        repository.updateAllUrls(updatedUrls)
    }

    fun updateGroupsOrder(orderedGroups: List<UrlGroup>) = viewModelScope.launch {
        val updatedGroups = orderedGroups.mapIndexed { index, group ->
            group.copy(orderIndex = index)
        }
        if (updatedGroups.isNotEmpty()) {
            repository.updateGroups(updatedGroups)
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

    fun updateGroup(group: UrlGroup) = viewModelScope.launch {
        repository.updateGroup(group)
    }

    // 获取所有启用的URL
    fun getEnabledUrls(): List<SearchUrl> {
        return allUrls.value.filter { it.isEnabled }
    }

    // 设置所有URL的选中状态
    fun setAllSelected(selected: Boolean) = viewModelScope.launch {
        try {
            val updatedUrls = allUrls.value.map { it.copy(isEnabled = selected) }
            repository.updateAllUrls(updatedUrls)
            Log.d(TAG, "Set all URLs selected: $selected")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting all URLs selected: $selected", e)
        }
    }

    // 在链接选中状态改变时检查和更新分组状态
    fun onUrlCheckChanged(searchUrl: SearchUrl, isChecked: Boolean) = viewModelScope.launch {
        // 更新链接的启用状态
        updateUrl(searchUrl.copy(isEnabled = isChecked))

        // 获取当前分组中所有链接
        val urlsInGroup = allUrls.value.filter { it.groupId == searchUrl.groupId }

        // 检查分组状态是否需要更新
        val groupShouldBeChecked = urlsInGroup.isNotEmpty() && urlsInGroup.all {
            if (it.id == searchUrl.id) isChecked else it.isEnabled
        }

        // 查找并更新对应的分组
        val group = allGroups.value.find { it.id == searchUrl.groupId } ?: return@launch

        // 通知UI更新分组状态
        _groupCheckEvents.postValue(Pair(group, groupShouldBeChecked))
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
