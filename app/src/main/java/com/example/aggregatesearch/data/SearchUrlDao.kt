package com.example.aggregatesearch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchUrlDao {
    
    // SearchUrl 相关操作
    @Query("SELECT * FROM search_urls ORDER BY orderIndex ASC")
    fun getAllUrls(): Flow<List<SearchUrl>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchUrl: SearchUrl): Long
    
    @Update
    suspend fun update(searchUrl: SearchUrl)
    
    @Update
    suspend fun updateAll(searchUrls: List<SearchUrl>)
    
    @Query("UPDATE search_urls SET cookie = :cookie WHERE id = :id")
    suspend fun updateCookie(id: Long, cookie: String)
    
    @Query("UPDATE search_urls SET autoCookie = :autoCookie WHERE id = :id")
    suspend fun updateAutoCookie(id: Long, autoCookie: String)
    
    @Delete
    suspend fun delete(searchUrl: SearchUrl)
    
    @Query("DELETE FROM search_urls")
    suspend fun deleteAllUrls()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrlWithId(url: SearchUrl)
    
    @Query("SELECT MAX(orderIndex) FROM search_urls WHERE groupId = :groupId")
    suspend fun getMaxOrderIndexForGroup(groupId: Long): Int?
    
    // UrlGroup 相关操作
    @Query("SELECT * FROM url_groups ORDER BY orderIndex ASC")
    fun getAllGroups(): Flow<List<UrlGroup>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: UrlGroup): Long
    
    @Update
    suspend fun updateGroup(group: UrlGroup)
    
    @Update
    suspend fun updateGroups(groups: List<UrlGroup>)
    
    @Delete
    suspend fun deleteGroup(group: UrlGroup)
    
    @Query("DELETE FROM url_groups")
    suspend fun deleteAllGroups()
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupWithId(group: UrlGroup): Long
    
    @Query("SELECT MAX(orderIndex) FROM url_groups")
    suspend fun getMaxGroupOrderIndex(): Int?
    
    @Query("SELECT COUNT(*) FROM url_groups")
    suspend fun getGroupCount(): Int
}
