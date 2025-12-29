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
    val name: String,
    val urlPattern: String,
    val urlPattern2: String = "",
    val isUrl2Selected: Boolean = false,
    var isEnabled: Boolean = true,
    val orderIndex: Int = 0,
    @ColumnInfo(name = "groupId") val groupId: Long, // Foreign key for UrlGroup
    val packageName: String = "", // 用于存储对应的应用包名，默认为空
    val useTextIcon: Boolean = false, // 是否使用文字图标
    val iconText: String = "", // 文字图标内容
    val iconBackgroundColor: Int = 0xFF2196F3.toInt(), // 文字图标背景颜色，默认蓝色
    val userAgent: String = "mobile", // 浏览器UA，默认为mobile
    val cookie: String = "", // 用户手动输入的Cookie
    val autoCookie: String = "" // 自动保存的Cookie
) {
    @androidx.room.Ignore
    var forceRefresh: Boolean = false // 用于强制刷新图标，不存入数据库
}
