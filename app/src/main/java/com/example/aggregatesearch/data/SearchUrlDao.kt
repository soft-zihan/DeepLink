package com.example.aggregatesearch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchUrlDao {
    // Query to get all URLs, ordered by their orderIndex
    @Query("SELECT * FROM search_urls ORDER BY orderIndex ASC")
    fun getAllUrls(): Flow<List<SearchUrl>>

    // Get the highest orderIndex currently in use
    @Query("SELECT MAX(orderIndex) FROM search_urls")
    suspend fun getMaxOrderIndex(): Int?

    // Methods for UrlGroup
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: UrlGroup): Long // Return the id of the inserted group

    @Update
    suspend fun updateGroup(group: UrlGroup)

    @Delete
    suspend fun deleteGroup(group: UrlGroup)

    @Query("SELECT * FROM url_groups ORDER BY orderIndex ASC")
    fun getAllGroups(): Flow<List<UrlGroup>>

    // Get URLs for a specific group
    @Query("SELECT * FROM search_urls WHERE groupId = :groupId ORDER BY orderIndex ASC")
    fun getUrlsByGroup(groupId: Long): Flow<List<SearchUrl>>

    // Update multiple groups (for reordering)
    @Update
    suspend fun updateGroups(groups: List<UrlGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchUrl: SearchUrl): Long

    @Update
    suspend fun update(searchUrl: SearchUrl)

    // Added to update a list of SearchUrls, useful for reordering
    @Update
    suspend fun updateAll(searchUrls: List<SearchUrl>)

    @Delete
    suspend fun delete(searchUrl: SearchUrl)

    @Query("SELECT MAX(orderIndex) FROM search_urls WHERE groupId = :groupId")
    suspend fun getMaxOrderIndexForGroup(groupId: Long): Int?

    @Query("SELECT MAX(orderIndex) FROM url_groups")
    suspend fun getMaxGroupOrderIndex(): Int?

    // 为备份恢复功能添加的方法
    @Query("DELETE FROM url_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM search_urls")
    suspend fun deleteAllUrls()

    // 插入带有ID的分组(用于恢复备份)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupWithId(group: UrlGroup): Long

    // 插入带有ID的URL(用于恢复备份)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrlWithId(url: SearchUrl)

    @Query("SELECT COUNT(*) FROM url_groups")
    suspend fun getGroupCount(): Int
}
