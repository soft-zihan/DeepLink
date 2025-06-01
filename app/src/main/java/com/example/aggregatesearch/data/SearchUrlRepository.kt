package com.example.aggregatesearch.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull // Required for getting a single value from Flow

class SearchUrlRepository(private val searchUrlDao: SearchUrlDao) {

    val allUrls: Flow<List<SearchUrl>> = searchUrlDao.getAllUrls()
    val allGroups: Flow<List<UrlGroup>> = searchUrlDao.getAllGroups()

    suspend fun insert(searchUrl: SearchUrl) {
        // The logic for orderIndex should be handled in ViewModel or before calling this
        searchUrlDao.insert(searchUrl)
    }

    suspend fun update(searchUrl: SearchUrl) {
        searchUrlDao.update(searchUrl)
    }

    suspend fun updateAllUrls(searchUrls: List<SearchUrl>) {
        searchUrlDao.updateAll(searchUrls)
    }

    suspend fun delete(searchUrl: SearchUrl) {
        searchUrlDao.delete(searchUrl)
    }

    suspend fun insertGroup(group: UrlGroup): Long {
        return searchUrlDao.insertGroup(group)
    }

    suspend fun updateGroup(group: UrlGroup) {
        searchUrlDao.updateGroup(group)
    }

    suspend fun updateGroups(groups: List<UrlGroup>) {
        searchUrlDao.updateGroups(groups)
    }

    suspend fun deleteGroup(group: UrlGroup) {
        searchUrlDao.deleteGroup(group)
    }

    suspend fun getMaxOrderIndexForGroup(groupId: Long): Int? {
        return searchUrlDao.getMaxOrderIndexForGroup(groupId)
    }

    suspend fun getMaxGroupOrderIndex(): Int? {
        return searchUrlDao.getMaxGroupOrderIndex()
    }

    // 新增备份恢复功能所需方法
    suspend fun deleteAllGroups() {
        searchUrlDao.deleteAllGroups()
    }

    suspend fun deleteAllUrls() {
        searchUrlDao.deleteAllUrls()
    }

    suspend fun insertGroupWithId(group: UrlGroup): Long {
        return searchUrlDao.insertGroupWithId(group)
    }

    suspend fun insertUrlWithId(url: SearchUrl) {
        searchUrlDao.insertUrlWithId(url)
    }
}
