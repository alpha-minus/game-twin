package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for an open browser tab. Per-vault, URL-only persistence.
 */
@Entity(
    tableName = "browser_tabs",
    foreignKeys = [ForeignKey(
        entity = VaultEntity::class,
        parentColumns = ["id"],
        childColumns = ["vaultId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["vaultId"])]
)
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val url: String,
    val title: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val faviconBytes: ByteArray?,
    @ColumnInfo(defaultValue = "0") val scrollY: Int = 0,
    @ColumnInfo(name = "tab_order", defaultValue = "0") val tabOrder: Int = 0,
    @ColumnInfo(name = "history_json", defaultValue = "[]") val historyJson: String = "[]",
    @ColumnInfo(name = "history_index", defaultValue = "0") val historyIndex: Int = 0,
    val lastAccessed: Long,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrowserTabEntity) return false

        if (id != other.id) return false
        if (vaultId != other.vaultId) return false
        if (url != other.url) return false
        if (title != other.title) return false
        if (faviconBytes != null) {
            if (other.faviconBytes == null) return false
            if (!faviconBytes.contentEquals(other.faviconBytes)) return false
        } else if (other.faviconBytes != null) return false
        if (scrollY != other.scrollY) return false
        if (tabOrder != other.tabOrder) return false
        if (historyJson != other.historyJson) return false
        if (historyIndex != other.historyIndex) return false
        if (lastAccessed != other.lastAccessed) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (faviconBytes?.contentHashCode() ?: 0)
        result = 31 * result + scrollY
        result = 31 * result + tabOrder
        result = 31 * result + historyJson.hashCode()
        result = 31 * result + historyIndex
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
