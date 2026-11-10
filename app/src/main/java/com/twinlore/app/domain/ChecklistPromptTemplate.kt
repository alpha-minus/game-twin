package com.twinlore.app.domain

data class ChecklistPromptTemplate(
    val id: String,   // stable default-id slug for defaults; UUID for future user-added ones
    val name: String,
    val content: String, // may contain {vault} placeholder
)
