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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var name: String,
    var urlPattern: String,
    var isEnabled: Boolean = true,
    var orderIndex: Int = 0,
    @ColumnInfo(name = "groupId") var groupId: Long, // Foreign key for UrlGroup
    var packageName: String = "", // 用于存储对应的应用包名，默认为空
    var useTextIcon: Boolean = false, // 是否使用文字图标
    var iconText: String = "", // 文字图标内容
    var iconBackgroundColor: Int = 0xFF2196F3.toInt() // 文字图标背景颜色，默认蓝色
) {
    @androidx.room.Ignore
    var forceRefresh: Boolean = false // 用于强制刷新图标，不存入数据库
}
