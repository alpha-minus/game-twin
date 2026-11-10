package com.twinlore.app.data

import android.content.Context
import com.twinlore.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing open source license information.
 *
 * `getAllLicenses()` is a projection of [OpenSourceLicenseCatalog]: it cannot list an entry the
 * catalog doesn't have, and looks up each entry's version from [OpenSourceLicenseNames] by
 * name, so it cannot list one with the wrong version either. A Context is needed only to
 * resolve each entry's [LicenseTextSource] into actual text, never to decide which entries
 * exist.
 */
@Singleton
class OpenSourceLicenseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val assetCache = mutableMapOf<String, String>()

    /**
     * Get all library licenses grouped by license type
     */
    fun getAllLicenses(): Map<LicenseType, List<LibraryLicense>> {
        val versions = OpenSourceLicenseNames.VERSIONS
        val sharedTexts = mapOf(
            SharedLicenseText.APACHE_2_0 to loadLicenseText(R.raw.apache_2_0_license),
            SharedLicenseText.MIT to loadLicenseText(R.raw.mit_license),
            SharedLicenseText.OFL_1_1 to loadLicenseText(R.raw.ofl_1_1_license),
        )

        val catalogLicenses = OpenSourceLicenseCatalog.ENTRIES.map { entry ->
            LibraryLicense(
                name = entry.name,
                version = versions.getValue(entry.name),
                license = entry.license,
                copyright = entry.copyright,
                licenseText = resolveText(entry.textSource, sharedTexts)
            )
        }

        // Not a distributed dependency (no Maven coordinate, no version), so it lives outside
        // the catalog; see the note on OpenSourceLicenseCatalog.
        val thirdPartyApis = listOf(
            LibraryLicense(
                name = "RetroAchievements API",
                version = "API",
                license = LicenseType.THIRD_PARTY_API,
                copyright = "RetroAchievements (https://retroachievements.org)",
                licenseText = """
                    This app uses the RetroAchievements API to display game achievements and metadata.
                    Data is provided by RetroAchievements and its community.
                    API documentation: https://api-docs.retroachievements.org/
                    Use of the API is subject to RetroAchievements' terms and community guidelines.
                """.trimIndent()
            ),
        )

        return (catalogLicenses + thirdPartyApis).groupBy { it.license }
    }

    private fun resolveText(source: LicenseTextSource, sharedTexts: Map<SharedLicenseText, String>): String =
        when (source) {
            is LicenseTextSource.Shared -> sharedTexts.getValue(source.family)
            is LicenseTextSource.Asset -> loadAssetText(source.path)
        }

    /**
     * Load license text from raw resource
     */
    private fun loadLicenseText(resourceId: Int): String {
        return try {
            context.resources.openRawResource(resourceId)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            "License text not available"
        }
    }

    /**
     * Load license text from a bundled asset file, memoised so repeated reads (e.g. the pdf.js
     * sub-component entries) don't hit disk again.
     */
    private fun loadAssetText(path: String): String = assetCache.getOrPut(path) {
        try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "License text not available"
        }
    }
}
