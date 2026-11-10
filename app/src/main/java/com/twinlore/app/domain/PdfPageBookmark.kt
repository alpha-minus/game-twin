package com.twinlore.app.domain

data class PdfPageBookmark(
    val id: String,
    val name: String,
    val pageNumber: Int,
    val order: Int
)
