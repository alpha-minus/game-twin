package com.twinlore.app.domain

data class ResourceFile(
    val id: String,
    val vaultId: String?,
    val kind: ResourceKind,
    val filename: String,
    val sizeBytes: Long,
    val homeRelativePath: String?,
    val absolutePath: String?,
    val mimeType: String?,
    val source: ResourceSource,
    val lastSeenAt: Long,
    val createdAt: Long
)

enum class ResourceSource(val raw: String) {
    BROWSER_SAVE("browser_save"),
    USER_ADD("user_add"),
    AUTO_SCRAPE("auto_scrape"),
    SCREENSHOT("screenshot"),
    MIGRATED("migrated"),
    LOCAL_LINK("local_link");

    companion object {
        fun fromRaw(raw: String): ResourceSource = values().firstOrNull { it.raw == raw } ?: USER_ADD
    }
}
