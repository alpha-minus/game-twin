package com.twinlore.app.data

import com.twinlore.app.R
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.isIconTierUnlocked

/**
 * Single source of truth for the selectable app icons. Each entry drives three
 * surfaces: the OS launcher icon (via [aliasClass]), the library top-bar emblem
 * ([emblemRes]/[emblemTinted]), and the startup splash ([splashLogoRes]/[splashBgColor]).
 *
 * Free: DEFAULT, PIXEL. Gold Edition (T1): GOLD. Cyber-Demon Edition (T2): CYBER, DEMONIC.
 * Noir-Adventure Edition (T3): ADVENTURE, NOIR.
 */
enum class AppIcon(
    val id: String,
    val displayName: String,
    val requiredTier: LicenseTier,
    /** Fully-qualified activity-alias class name toggled by [AppIconManager]. */
    val aliasClass: String,
    val launcherMipmap: Int,
    val emblemRes: Int,
    /** When true the emblem is a monochrome mask tinted to the theme (free icons). */
    val emblemTinted: Boolean,
    val splashLogoRes: Int,
    val splashBgColor: Long,
    /** Settings-picker thumbnail. */
    val previewRes: Int,
    /**
     * The folded handle's battery ring and charging bolt, picked by eye from
     * the icon's own art. The splash colours are all near black, so nothing
     * usable could be derived from them.
     */
    val handleAccent: Long,
) {
    DEFAULT(
        id = "default", displayName = "Default", requiredTier = LicenseTier.NONE,
        aliasClass = "com.twinlore.app.MainActivityDefault",
        launcherMipmap = R.mipmap.ic_launcher,
        emblemRes = R.drawable.app_emblem_default, emblemTinted = true,
        splashLogoRes = R.mipmap.ic_launcher_foreground, splashBgColor = 0xFF3A096EL,
        previewRes = R.drawable.preview_default,
        handleAccent = 0xFF9BE8B0L,
    ),
    PIXEL(
        id = "pixel", displayName = "Pixel", requiredTier = LicenseTier.NONE,
        aliasClass = "com.twinlore.app.MainActivityPixel",
        launcherMipmap = R.mipmap.ic_launcher_pixel,
        emblemRes = R.drawable.app_emblem_pixel, emblemTinted = true,
        splashLogoRes = R.drawable.splash_pixel, splashBgColor = 0xFF360966L,
        previewRes = R.drawable.splash_pixel,
        handleAccent = 0xFFB9F5C9L,
    ),
    GOLD(
        id = "gold", displayName = "Gold", requiredTier = LicenseTier.TIER1,
        aliasClass = "com.twinlore.app.MainActivityGold",
        launcherMipmap = R.mipmap.ic_launcher_gold,
        emblemRes = R.drawable.emblem_gold, emblemTinted = false,
        splashLogoRes = R.drawable.splash_gold, splashBgColor = 0xFF12051DL,
        previewRes = R.drawable.splash_gold,
        handleAccent = 0xFFE6A84AL,
    ),
    CYBER(
        id = "cyber", displayName = "Cyber", requiredTier = LicenseTier.TIER2,  // Cyber-Demon Edition
        aliasClass = "com.twinlore.app.MainActivityCyber",
        launcherMipmap = R.mipmap.ic_launcher_cyber,
        emblemRes = R.drawable.emblem_cyber, emblemTinted = false,
        splashLogoRes = R.drawable.splash_cyber, splashBgColor = 0xFF060721L,
        previewRes = R.drawable.splash_cyber,
        handleAccent = 0xFF3FE0FFL,
    ),
    DEMONIC(
        id = "demonic", displayName = "Demonic", requiredTier = LicenseTier.TIER2,
        aliasClass = "com.twinlore.app.MainActivityDemonic",
        launcherMipmap = R.mipmap.ic_launcher_demonic,
        emblemRes = R.drawable.emblem_demonic, emblemTinted = false,
        splashLogoRes = R.drawable.splash_demonic, splashBgColor = 0xFF080708L,
        previewRes = R.drawable.splash_demonic,
        handleAccent = 0xFFFF2A3AL,
    ),
    ADVENTURE(
        id = "adventure", displayName = "Adventure", requiredTier = LicenseTier.TIER3,  // Noir-Adventure Edition
        aliasClass = "com.twinlore.app.MainActivityAdventure",
        launcherMipmap = R.mipmap.ic_launcher_adventure,
        emblemRes = R.drawable.emblem_adventure, emblemTinted = false,
        splashLogoRes = R.drawable.splash_adventure, splashBgColor = 0xFF0B0217L,
        previewRes = R.drawable.splash_adventure,
        handleAccent = 0xFF4CE08AL,
    ),
    // The label reads "Noir"; the id, the activity-alias and the resource names keep the old
    // "noire" spelling on purpose. [id] is what DataStore stores, so changing it would reset
    // everyone's chosen icon, and [aliasClass] names a manifest component whose enabled state
    // PackageManager persists — renaming it can leave an upgrading device with no launcher entry.
    NOIR(
        id = "noire", displayName = "Noir", requiredTier = LicenseTier.TIER3,  // Noir-Adventure Edition
        aliasClass = "com.twinlore.app.MainActivityNoire",
        launcherMipmap = R.mipmap.ic_launcher_noire,
        emblemRes = R.drawable.emblem_noire, emblemTinted = false,
        splashLogoRes = R.drawable.splash_noire, splashBgColor = 0xFF060606L,
        previewRes = R.drawable.splash_noire,
        handleAccent = 0xFFD8D8D8L,
    );

    fun isUnlockedBy(tier: LicenseTier): Boolean = isIconTierUnlocked(requiredTier, tier)

    companion object {
        fun fromId(id: String?): AppIcon = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * The Pixel icon is free but must be earned: it unlocks once the checklist
 * tap-game high score reaches this value.
 */
const val PIXEL_UNLOCK_TAP_SCORE = 120

/**
 * The icons a user can pick without buying or earning anything first: those
 * their tier unlocks, with Pixel held back until the tap game has earned it.
 * The same rule as the Settings picker's.
 */
fun selectableAppIcons(tier: LicenseTier, tapHighScore: Int): List<AppIcon> =
    AppIcon.entries.filter {
        it.isUnlockedBy(tier) && (it != AppIcon.PIXEL || tapHighScore >= PIXEL_UNLOCK_TAP_SCORE)
    }

/** The stored icon if the tier permits it, else [AppIcon.DEFAULT]. */
fun effectiveAppIcon(stored: AppIcon, tier: LicenseTier): AppIcon =
    if (stored.isUnlockedBy(tier)) stored else AppIcon.DEFAULT

/**
 * Effective icon = stored selection gated by the current license tier.
 * Shared by SettingsViewModel and MainActivity so both agree.
 */
fun effectiveAppIconFlow(
    settings: SettingsRepository,
    license: com.twinlore.app.data.license.LicenseManagerUiStateContract,
): kotlinx.coroutines.flow.Flow<AppIcon> =
    kotlinx.coroutines.flow.combine(settings.appIcon, license.licenseTier) { stored, tier ->
        effectiveAppIcon(stored, tier)
    }
