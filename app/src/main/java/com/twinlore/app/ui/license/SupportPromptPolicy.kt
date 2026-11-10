package com.twinlore.app.ui.license

// The prompt fires at value events (vault created, journey export completed),
// at most SUPPORT_PROMPT_MAX_SHOWN times per install, at least
// SUPPORT_PROMPT_SPACING_MS apart.

/** Minimum spacing between two prompts. */
const val SUPPORT_PROMPT_SPACING_MS: Long = 7L * 24 * 60 * 60 * 1000

/** Lifetime cap on automatic prompts. */
const val SUPPORT_PROMPT_MAX_SHOWN: Int = 2

/**
 * One-shot prompt credits. Declaration order is consumption priority: lowest
 * threshold first, vaults before exports on ties. [storageValue] is persisted
 * in DataStore ("support_milestones_consumed"); never rename a value.
 */
enum class SupportMilestone(val storageValue: String) {
    VAULTS_3("vaults3"),
    EXPORTS_3("exports3"),
    VAULTS_10("vaults10"),
    EXPORTS_10("exports10"),
    ;

    fun crossed(vaultCount: Int, exportCount: Int): Boolean = when (this) {
        VAULTS_3 -> vaultCount >= 3
        EXPORTS_3 -> exportCount >= 3
        VAULTS_10 -> vaultCount >= 10
        EXPORTS_10 -> exportCount >= 10
    }
}

/**
 * The milestone to consume if the support prompt should show at this value
 * event, or null to stay quiet. A crossed-but-blocked milestone (same-day
 * collision, spacing window) simply stays unconsumed and fires at a later
 * event. The very first prompt ignores [SUPPORT_PROMPT_SPACING_MS]: a legacy
 * monthly-prompt timestamp may predate the milestone model, and a shown-count
 * of zero means this model has never prompted.
 */
fun supportPromptMilestone(
    nowMs: Long,
    lastShownMs: Long,
    licensed: Boolean,
    promptsShown: Int,
    vaultCount: Int,
    exportCount: Int,
    consumed: Set<String>,
): SupportMilestone? {
    if (licensed) return null
    if (promptsShown >= SUPPORT_PROMPT_MAX_SHOWN) return null
    if (promptsShown > 0 && nowMs - lastShownMs < SUPPORT_PROMPT_SPACING_MS) return null
    return SupportMilestone.entries.firstOrNull {
        it.storageValue !in consumed && it.crossed(vaultCount, exportCount)
    }
}
