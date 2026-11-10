package com.twinlore.app.domain

sealed class AddFileResult {
    data class Copied(val resourceFileId: String) : AddFileResult()
    data class Failed(val cause: Throwable) : AddFileResult()
}
