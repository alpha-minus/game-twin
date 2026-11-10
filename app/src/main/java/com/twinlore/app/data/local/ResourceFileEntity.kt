package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "resource_files",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vault_id"]),
        Index(value = ["vault_id", "kind"]),
        Index(value = ["filename", "size_bytes"])
    ]
)
data class ResourceFileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vault_id") val vaultId: String?,
    val kind: String,
    val filename: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "home_relative_path") val homeRelativePath: String?,
    @ColumnInfo(name = "absolute_path") val absolutePath: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val source: String,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
