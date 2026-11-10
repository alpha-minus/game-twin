package com.twinlore.app.domain

import java.util.concurrent.TimeUnit

/**
 * One row of the library's Delete multiple picker: a vault plus the reasons it might be
 * worth cleaning up. The picker's quick-select filters tick vaults by these flags.
 */
data class VaultCleanupInfo(
    val vault: Vault,
    /** Whole days since the vault was last opened, or since it was created if never opened. */
    val daysIdle: Long,
    val neverOpened: Boolean,
    val missingFileCount: Int,
    val isEmpty: Boolean,
    val guideCount: Int = 0,
    val imageCount: Int = 0,
    /** Bytes in the library folder that deleting this vault frees. */
    val totalBytes: Long = 0,
    /** Bytes that "Guides and images only" frees. */
    val guideImageBytes: Long = 0,
) {
    val isStale: Boolean get() = daysIdle > VaultCleanupClassifier.STALE_DAYS
    val hasMissingFiles: Boolean get() = missingFileCount > 0

    /** Short tags for the row's subtitle, empty when nothing flags the vault. */
    val reasonLabels: List<String>
        get() = buildList {
            if (isStale) add(if (neverOpened) "Never opened" else "Not opened in $daysIdle days")
            if (hasMissingFiles) add(if (missingFileCount == 1) "1 missing file" else "$missingFileCount missing files")
            if (isEmpty) add("Empty")
        }
}

/** "512 B", "1.5 KB", "12.3 MB", "1.25 GB". */
fun formatVaultSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

object VaultCleanupClassifier {
    /** "Not opened in more than 3 months". */
    const val STALE_DAYS = 90L

    /**
     * @param contentCounts per-vault count of non-artwork resources and user content;
     *   a vault absent from the map counts as empty.
     * @param missingCounts per-vault count of resource files that no longer exist.
     * @param guideCounts per-vault count of guides, and [imageCounts] of Image tab
     *   entries ("Guides and images only" removes both).
     * @param sizes per-vault (total bytes, guide bytes) in the library folder.
     */
    fun classify(
        vaults: List<Vault>,
        contentCounts: Map<String, Int>,
        missingCounts: Map<String, Int>,
        nowMs: Long,
        guideCounts: Map<String, Int> = emptyMap(),
        imageCounts: Map<String, Int> = emptyMap(),
        sizes: Map<String, Pair<Long, Long>> = emptyMap(),
    ): List<VaultCleanupInfo> = vaults.map { vault ->
        val reference = (vault.lastOpened ?: vault.createdAt).time
        VaultCleanupInfo(
            vault = vault,
            daysIdle = TimeUnit.MILLISECONDS.toDays((nowMs - reference).coerceAtLeast(0L)),
            neverOpened = vault.lastOpened == null,
            missingFileCount = missingCounts[vault.id] ?: 0,
            isEmpty = (contentCounts[vault.id] ?: 0) == 0,
            guideCount = guideCounts[vault.id] ?: 0,
            imageCount = imageCounts[vault.id] ?: 0,
            totalBytes = sizes[vault.id]?.first ?: 0,
            guideImageBytes = sizes[vault.id]?.second ?: 0,
        )
    }
}
