package com.example.aggregatesearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "url_groups")
data class UrlGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    var isExpanded: Boolean = true,
    var orderIndex: Int = 0,
    var color: String? = null // 新增颜色字段，可为空
)
