package com.example.aggregatesearch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_urls",
    foreignKeys = [ForeignKey(
        entity = UrlGroup::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["groupId"])]
)
data class SearchUrl(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val urlPattern: String,
    var isEnabled: Boolean = true,
    var orderIndex: Int = 0,
    @ColumnInfo(name = "groupId") var groupId: Long // Foreign key for UrlGroup
)
