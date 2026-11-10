package com.twinlore.app.domain

/**
 * Health of a media search path's underlying URI/file.
 * - [Ok]: the folder resolves and is readable. The current display name is
 *   surfaced so the UI can offer to relabel if it drifted from the stored
 *   label (e.g. user renamed the folder in their file manager but SAF kept
 *   the URI alive).
 * - [Missing]: the folder no longer resolves (renamed, moved, deleted, or
 *   permission revoked). Scans will skip this entry.
 */
sealed class MediaSearchPathStatus {
    data class Ok(val currentName: String?) : MediaSearchPathStatus()
    object Missing : MediaSearchPathStatus()
}
