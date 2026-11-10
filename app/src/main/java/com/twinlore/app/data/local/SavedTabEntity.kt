package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity representing a saved browser tab for a vault
 */
@Entity(
    tableName = "saved_tabs",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vaultId"])]
)
data class SavedTabEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Vault ID this tab belongs to
     */
    val vaultId: String,
    
    /**
     * Tab title/name (user-editable)
     */
    val title: String,
    
    /**
     * Last visited URL
     */
    val url: String,
    
    /**
     * Scroll position (vertical scroll in pixels)
     */
    val scrollPosition: Int = 0,
    
    /**
     * Zoom level (0.5 to 3.0)
     */
    val zoomLevel: Float = 1.0f,
    
    /**
     * Is this the favorite tab?
     */
    val isFavorite: Boolean = false,
    
    /**
     * Is this marked as "Map"?
     */
    val isMap: Boolean = false,
    
    /**
     * Is this marked as "Current Content"?
     */
    val isCurrentContent: Boolean = false,
    
    /**
     * Tab order/position in the list
     */
    @ColumnInfo(name = "tab_order")
    val order: Int = 0,
    
    /**
     * When the tab was created
     */
    val createdAt: Date,
    
    /**
     * Last time the tab was accessed
     */
    val lastAccessed: Date,

    /**
     * Guide file type: "html", "txt", or "pdf"
     */
    val fileType: String = "html",

    /**
     * Original file name (e.g. "guide.pdf", "walkthrough.txt")
     */
    val fileName: String? = null,

    /**
     * Last viewed page number for PDF guides (0-based)
     */
    val lastPdfPage: Int = 0,

    /**
     * Scroll offset within page for PDF guides (0.0 to 1.0)
     */
    val pdfScrollOffset: Float = 0f,

    /**
     * FK to resource_files for offline guides. Null for online URL-only tabs.
     */
    @ColumnInfo(name = "resource_id")
    val resourceId: String? = null,

    /**
     * Anchor-based scroll restore: normalized text at viewport top (alphanumeric, lowercase).
     * Null = ratio-only restore. Replaces the bookmark_metadata.json field of the same name.
     */
    @ColumnInfo(name = "scroll_normalized_anchor")
    val scrollNormalizedAnchor: String? = null,

    /**
     * 1-based index of the anchor text in the document. 0 when anchor is null.
     */
    @ColumnInfo(name = "scroll_occurrence_index")
    val scrollOccurrenceIndex: Int = 0,

    /**
     * scrollY / pageHeight ratio used as fallback when anchor is not found.
     */
    @ColumnInfo(name = "scroll_ratio")
    val scrollRatio: Double = 0.0,

    /**
     * Active scroll marker id (for L1/R1 jump cycle). Replaces bookmark_metadata.json's
     * focusedScrollMarkerId.
     */
    @ColumnInfo(name = "focused_scroll_marker_id")
    val focusedScrollMarkerId: String? = null,

    /**
     * Favicon bytes (PNG), captured from WebView at link save time.
     * Null for non-link bookmarks and for pre-migration rows.
     */
    @ColumnInfo(name = "favicon_bytes", typeAffinity = ColumnInfo.BLOB)
    val faviconBytes: ByteArray? = null,

    /**
     * For fileType="group" rows, points at the page_group_entries.id the
     * user was last reading. Null for non-group rows.
     */
    @ColumnInfo(name = "group_active_entry_id")
    val groupActiveEntryId: String? = null,

    /**
     * Sanitized title captured once at save time so the on-disk folder
     * (`<vault>/<group_folder_name>/page-N.mht`) is stable even if the
     * user later renames the group via the UI. Null for non-group rows
     * and for legacy v0 groups created before the v31→v32 migration —
     * the on-disk resolver falls back to the old `groups/<groupTabId>/`
     * layout in that case.
     */
    @ColumnInfo(name = "group_folder_name")
    val groupFolderName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedTabEntity) return false
        if (id != other.id) return false
        if (vaultId != other.vaultId) return false
        if (title != other.title) return false
        if (url != other.url) return false
        if (scrollPosition != other.scrollPosition) return false
        if (zoomLevel != other.zoomLevel) return false
        if (isFavorite != other.isFavorite) return false
        if (isMap != other.isMap) return false
        if (isCurrentContent != other.isCurrentContent) return false
        if (order != other.order) return false
        if (createdAt != other.createdAt) return false
        if (lastAccessed != other.lastAccessed) return false
        if (fileType != other.fileType) return false
        if (fileName != other.fileName) return false
        if (lastPdfPage != other.lastPdfPage) return false
        if (pdfScrollOffset != other.pdfScrollOffset) return false
        if (resourceId != other.resourceId) return false
        if (scrollNormalizedAnchor != other.scrollNormalizedAnchor) return false
        if (scrollOccurrenceIndex != other.scrollOccurrenceIndex) return false
        if (scrollRatio != other.scrollRatio) return false
        if (focusedScrollMarkerId != other.focusedScrollMarkerId) return false
        if (faviconBytes != null) {
            if (other.faviconBytes == null) return false
            if (!faviconBytes.contentEquals(other.faviconBytes)) return false
        } else if (other.faviconBytes != null) return false
        if (groupActiveEntryId != other.groupActiveEntryId) return false
        if (groupFolderName != other.groupFolderName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + scrollPosition
        result = 31 * result + zoomLevel.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + isMap.hashCode()
        result = 31 * result + isCurrentContent.hashCode()
        result = 31 * result + order
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastAccessed.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + lastPdfPage
        result = 31 * result + pdfScrollOffset.hashCode()
        result = 31 * result + (resourceId?.hashCode() ?: 0)
        result = 31 * result + (scrollNormalizedAnchor?.hashCode() ?: 0)
        result = 31 * result + scrollOccurrenceIndex
        result = 31 * result + scrollRatio.hashCode()
        result = 31 * result + (focusedScrollMarkerId?.hashCode() ?: 0)
        result = 31 * result + (faviconBytes?.contentHashCode() ?: 0)
        result = 31 * result + (groupActiveEntryId?.hashCode() ?: 0)
        result = 31 * result + (groupFolderName?.hashCode() ?: 0)
        return result
    }
}

