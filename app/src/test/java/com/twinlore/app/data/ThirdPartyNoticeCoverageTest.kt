package com.twinlore.app.data

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Fails when a distributed dependency is added without a third-party notice.
 *
 * This is the regression that already happened twice: telephoto and security-crypto shipped for
 * months with no attribution because the notice list is maintained by hand. Only distributed
 * configurations are checked; test and debug-only dependencies are not shipped, so they carry no
 * attribution duty.
 */
class ThirdPartyNoticeCoverageTest {

    /** Maven coordinate prefix to the notice name that covers it. */
    private val coverage = mapOf(
        "androidx.core:core-ktx" to "AndroidX Core KTX",
        "androidx.multidex:multidex" to "AndroidX Multidex",
        "androidx.compose:compose-bom" to "Jetpack Compose BOM",
        "androidx.compose.ui:ui" to "Jetpack Compose UI",
        "androidx.compose.foundation:foundation" to "Jetpack Compose Foundation",
        "androidx.compose.material3:material3" to "Jetpack Compose Material3",
        "androidx.compose.material:material-icons-extended" to "Compose Material Icons Extended",
        "androidx.lifecycle:" to "AndroidX Lifecycle",
        "androidx.activity:activity-compose" to "AndroidX Activity Compose",
        "androidx.room:" to "Room Database",
        "com.google.dagger:hilt-android" to "Dagger Hilt",
        "androidx.hilt:hilt-navigation-compose" to "Hilt Navigation Compose",
        "org.jetbrains.kotlinx:kotlinx-coroutines" to "Kotlinx Coroutines",
        "com.google.code.gson:gson" to "Gson",
        "io.coil-kt:coil" to "Coil",
        "me.saket.telephoto:" to "Telephoto Zoomable Image",
        "com.squareup.retrofit2:" to "Retrofit",
        "com.squareup.okhttp3:" to "OkHttp",
        "androidx.datastore:" to "DataStore",
        "androidx.security:security-crypto" to "AndroidX Security Crypto",
        "androidx.documentfile:" to "DocumentFile",
        "org.jsoup:jsoup" to "JSoup",
        "androidx.webkit:" to "WebKit",
        "androidx.palette:" to "Palette",
    )

    // Escaped rather than a raw string on purpose: a raw string ending in \" runs into Kotlin's
    // """" ambiguity. Anchoring on ^\s*implementation( also excludes testImplementation,
    // androidTestImplementation and debugImplementation, none of which are distributed.
    private val distributed =
        Regex("^\\s*implementation\\(.*?\"([^\"]+:[^\"]+)\"", RegexOption.MULTILINE)

    @Test
    fun `every distributed dependency maps to a notice`() {
        val gradle = RepoFiles.at("app/build.gradle.kts").readText()
        val noticed = OpenSourceLicenseNames.ALL

        val uncovered = distributed.findAll(gradle)
            .map { it.groupValues[1] }
            .filter { coord -> coverage.keys.none { coord.startsWith(it) } }
            .toList()

        if (uncovered.isNotEmpty()) {
            fail(
                "No notice mapping for: ${uncovered.joinToString()}. Add the component to " +
                    "OpenSourceLicenseRepository and to this test's coverage map."
            )
        }

        for ((coord, name) in coverage) {
            if (gradle.contains(coord)) {
                assertTrue("'$name' covers $coord but is not in the repository", name in noticed)
            }
        }
    }

    /**
     * `OpenSourceLicenseNames.ALL` is what the two tests above check; `OpenSourceLicenseCatalog`
     * is what `OpenSourceLicenseRepository` actually renders. Nothing upstream of this test
     * forces the two to agree: a name could be dropped from the catalog while staying in
     * `VERSIONS`, and the app would ship silently missing a notice while the tests above stayed
     * green, because they never look at the catalog at all. This is the check that would have
     * caught that.
     */
    @Test
    fun `every version-tracked component has a catalog entry, and vice versa`() {
        assertEquals(OpenSourceLicenseNames.ALL, OpenSourceLicenseCatalog.NAMES)
    }

    @Test
    fun `every bundled font has a notice`() {
        val fonts = RepoFiles.asset("fonts").listFiles().orEmpty().filter { it.extension == "ttf" }
        assertTrue("expected bundled fonts", fonts.isNotEmpty())
        val noticed = OpenSourceLicenseNames.ALL.joinToString(" ").lowercase()
        for (font in fonts) {
            val stem = font.nameWithoutExtension.replace('_', ' ')
            assertTrue("no notice for bundled font ${font.name}", stem in noticed)
        }
    }

    /**
     * Asserts the other way round (does the noticed version appear in the shipped bundle?) rather
     * than parsing a version out of viewer.mjs. Picking "the first triple-dotted string literal in
     * a minified bundle" happens to be right today and would silently rot.
     */
    @Test
    fun `pdfjs is noticed at its bundled version`() {
        val noticed = OpenSourceLicenseNames.VERSIONS["PDF.js"]
        requireNotNull(noticed) { "PDF.js is bundled in assets but carries no notice" }
        val viewer = RepoFiles.asset("pdfjs/web/viewer.mjs").readText()
        assertTrue(
            "notices claim PDF.js $noticed but that version does not appear in the bundle",
            viewer.contains("\"$noticed\""),
        )
    }

    /**
     * A catalog entry can name an asset path that doesn't resolve, e.g. a typo, and nothing
     * catches it: [OpenSourceLicenseRepository] swallows the read failure and silently renders
     * "License text not available" in the shipped APK instead of failing loudly. The catalog is
     * `Context`-free, so this can be checked directly against the repo's asset files rather than
     * needing an instrumented test.
     */
    @Test
    fun `every catalog asset license text resolves to a real, non-empty file`() {
        val assetEntries = OpenSourceLicenseCatalog.ENTRIES
            .filter { it.textSource is LicenseTextSource.Asset }

        assertTrue("expected catalog entries with an Asset text source", assetEntries.isNotEmpty())

        for (entry in assetEntries) {
            val path = (entry.textSource as LicenseTextSource.Asset).path
            val file = RepoFiles.asset(path)
            assertTrue("${entry.name}: no asset file at $path", file.isFile)
            assertTrue("${entry.name}: asset file at $path is empty", file.length() > 0)
        }
    }
}
