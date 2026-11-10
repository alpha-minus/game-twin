package com.twinlore.app.ui.license

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides, at a value event (vault created, journey export completed), whether
 * the support prompt should show now, and records the show before the caller
 * displays it. The decision itself is [supportPromptMilestone]; this class only
 * moves state in and out of DataStore around it.
 *
 * Recording before display follows the old monthly prompt's pattern: if the
 * process dies in the moment between record and render, one prompt occurrence
 * is lost rather than one shown twice.
 */
@Singleton
class SupportPromptGate internal constructor(
    private val settings: SettingsRepository,
    private val license: LicenseManagerUiStateContract,
    private val vaultCount: suspend () -> Int,
    private val nowMs: () -> Long,
) {

    @Inject constructor(
        settings: SettingsRepository,
        licenseManager: LicenseManager,
        vaultRepository: VaultRepository,
    ) : this(
        settings = settings,
        license = licenseManager,
        vaultCount = { vaultRepository.getAllVaults().size },
        nowMs = System::currentTimeMillis,
    )

    /** A prompt to show now. [isFinal] appends the "last time we ask" line. */
    data class Prompt(val isFinal: Boolean)

    suspend fun onVaultCreated(): Prompt? = onValueEvent()

    suspend fun onExportCompleted(): Prompt? {
        settings.incrementJourneyExportCount()
        return onValueEvent()
    }

    private suspend fun onValueEvent(): Prompt? {
        migrateLegacyPromptState()
        val shown = settings.supportPromptCount.first()
        val milestone = supportPromptMilestone(
            nowMs = nowMs(),
            lastShownMs = settings.lastSupportPromptAt.first(),
            licensed = license.isLicensedNow(),
            promptsShown = shown,
            vaultCount = vaultCount(),
            exportCount = settings.journeyExportCount.first(),
            consumed = settings.supportMilestonesConsumed.first(),
        ) ?: return null
        settings.recordSupportPromptShown(milestone.storageValue, nowMs())
        return Prompt(isFinal = shown + 1 >= SUPPORT_PROMPT_MAX_SHOWN)
    }

    /**
     * A pre-milestone install that already saw the monthly prompt starts at one
     * prompt shown (with the lowest vault credit consumed), so the milestone
     * model asks such users at most once more. The legacy timestamp is kept so
     * the spacing window still counts from the last thing they actually saw.
     */
    private suspend fun migrateLegacyPromptState() {
        if (settings.supportPromptCount.first() > 0) return
        if (settings.supportMilestonesConsumed.first().isNotEmpty()) return
        if (settings.lastSupportPromptAt.first() <= 0L) return
        settings.setSupportPromptState(
            count = 1,
            consumed = setOf(SupportMilestone.VAULTS_3.storageValue),
        )
    }
}
