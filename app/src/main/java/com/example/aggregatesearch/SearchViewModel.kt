package com.example.aggregatesearch

import androidx.lifecycle.*
import com.example.aggregatesearch.data.SearchUrl
import com.example.aggregatesearch.data.SearchUrlRepository
import com.example.aggregatesearch.data.UrlGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class SearchViewModel(private val repository: SearchUrlRepository) : ViewModel() {

    val allUrls: StateFlow<List<SearchUrl>> = repository.allUrls
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allGroups: StateFlow<List<UrlGroup>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedItems: StateFlow<List<Any>> = combine(allGroups, allUrls) { groups, urls ->
        val items = mutableListOf<Any>()
        groups.sortedBy { it.orderIndex }.forEach { group ->
            items.add(group)
            if (group.isExpanded) {
                items.addAll(urls.filter { it.groupId == group.id }.sortedBy { it.orderIndex })
            }
        }
        items
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(searchUrl: SearchUrl) = viewModelScope.launch {
        val maxOrderIndex = repository.getMaxOrderIndexForGroup(searchUrl.groupId) ?: -1
        repository.insert(searchUrl.copy(orderIndex = maxOrderIndex + 1))
    }

    fun updateUrl(searchUrl: SearchUrl) = viewModelScope.launch {
        repository.update(searchUrl)
    }

    fun delete(searchUrl: SearchUrl) = viewModelScope.launch {
        repository.delete(searchUrl)
    }

    fun updateUrlsOrder(orderedUrls: List<SearchUrl>, targetGroupId: Long? = null) = viewModelScope.launch {
        val finalListToPersist = orderedUrls.mapIndexed { index, url ->
            url.copy(orderIndex = index, groupId = targetGroupId ?: url.groupId)
        }
        if (finalListToPersist.isNotEmpty()) {
            repository.updateAllUrls(finalListToPersist)
        }
    }

    fun addGroup(groupName: String) = viewModelScope.launch {
        val maxOrderIndex = repository.getMaxGroupOrderIndex() ?: -1
        val newGroup = UrlGroup(name = groupName, orderIndex = maxOrderIndex + 1)
        repository.insertGroup(newGroup)
    }

    fun deleteGroup(group: UrlGroup) = viewModelScope.launch {
        repository.deleteGroup(group)
    }

    fun updateGroupExpanded(group: UrlGroup, isExpanded: Boolean) = viewModelScope.launch {
        repository.updateGroup(group.copy(isExpanded = isExpanded))
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
