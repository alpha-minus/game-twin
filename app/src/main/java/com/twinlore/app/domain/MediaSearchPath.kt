package com.twinlore.app.domain

data class MediaSearchPath(
    val id: String,
    val uri: String,
    val label: String,
    val origin: Origin,
    val lastScannedAt: Long?,
    val createdAt: Long,
    val disabled: Boolean = false,
) {
    enum class Origin(val raw: String) {
        AUTO_ESDE("AUTO_ESDE"),
        AUTO_COCOON("AUTO_COCOON"),
        AUTO_HOME("AUTO_HOME"),
        MANUAL("MANUAL");

        companion object {
            fun fromRaw(raw: String): Origin = values().first { it.raw == raw }
        }
    }
}
