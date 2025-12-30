package com.example.aggregatesearch.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "url_groups")
data class UrlGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    var isExpanded: Boolean = true,
    val orderIndex: Int = 0,
    val color: String? = null
) {
    @Ignore
    var urls: List<SearchUrl> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UrlGroup

        if (id != other.id) return false
        if (name != other.name) return false
        if (isExpanded != other.isExpanded) return false
        if (orderIndex != other.orderIndex) return false
        if (color != other.color) return false
        if (urls != other.urls) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isExpanded.hashCode()
        result = 31 * result + orderIndex
        result = 31 * result + (color?.hashCode() ?: 0)
        result = 31 * result + urls.hashCode()
        return result
    }
}
