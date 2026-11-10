package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row metadata about the library snapshot. Travels with the .gametwin
 * file across devices so a future cloud-sync layer can detect conflicts.
 */
@Entity(tableName = "library_meta")
data class LibraryMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val schemaVersion: Int,
    val lastWrittenAtMillis: Long,
    val lastWrittenDeviceId: String,
    val lastWrittenDeviceLabel: String
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
