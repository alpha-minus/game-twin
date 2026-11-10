package com.twinlore.app.ui.license

import androidx.compose.ui.graphics.Color
import com.twinlore.app.data.license.LicenseTier

/**
 * Tier "metal" for a locked app-icon boundary/badge: bronze Gold, silver
 * Cyber-Demon, gold Noir-Adventure. Shared by the Settings picker tiles and
 * the support dialog's icon strip so a tier reads as the same colour
 * everywhere it appears.
 */
internal fun tierLockColor(tier: LicenseTier): Color = when (tier) {
    LicenseTier.TIER1 -> Color(0xFFCD7F32) // bronze — Gold Edition
    LicenseTier.TIER2 -> Color(0xFFC0C0C0) // silver — Cyber-Demon Edition
    LicenseTier.TIER3 -> Color(0xFFF6C64B) // gold — Noir-Adventure Edition
    LicenseTier.NONE -> Color.White
}
