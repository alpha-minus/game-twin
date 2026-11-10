package com.twinlore.app.domain

enum class ResourceKind(val raw: String) {
    ARTWORK("artwork"),
    MAP("map"),
    GUIDE("guide"),
    SCREENSHOT("screenshot");

    companion object {
        fun fromRaw(raw: String): ResourceKind? = values().firstOrNull { it.raw == raw }
    }
}
