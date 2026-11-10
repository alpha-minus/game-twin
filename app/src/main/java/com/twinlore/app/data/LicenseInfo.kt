package com.twinlore.app.data

/**
 * Data model for library license information
 */
data class LibraryLicense(
    val name: String,
    val version: String,
    val license: LicenseType,
    val copyright: String? = null,
    val licenseText: String
)

/**
 * License type enumeration
 */
enum class LicenseType(val displayName: String) {
    APACHE_2_0("Apache License 2.0"),
    MIT("MIT License"),
    BSD_2_CLAUSE("BSD 2-Clause License"),
    BSD_3_CLAUSE("BSD 3-Clause License"),
    OFL_1_1("SIL Open Font License 1.1"),
    CC0_1_0("CC0 1.0 Universal"),
    EPL_2_0("Eclipse Public License 2.0"),
    /** Third-party APIs and data sources (attribution / terms of use) */
    THIRD_PARTY_API("Third-party APIs & Data Sources"),
}

/** Notice names and versions, reachable without a Context so unit tests can assert coverage. */
object OpenSourceLicenseNames {
    /** Name to version, every distributed component. */
    val VERSIONS: Map<String, String> = mapOf(
        // Gradle dependencies
        "AndroidX Core KTX" to "1.12.0",
        "AndroidX Multidex" to "2.0.1",
        "Jetpack Compose BOM" to "2023.10.01",
        "Jetpack Compose UI" to "1.5.4",
        "Jetpack Compose Foundation" to "1.5.4",
        "Jetpack Compose Material3" to "1.1.2",
        "Compose Material Icons Extended" to "1.5.4",
        "AndroidX Lifecycle" to "2.6.2",
        "AndroidX Activity Compose" to "1.8.0",
        "Room Database" to "2.5.2",
        "Dagger Hilt" to "2.48",
        "Hilt Navigation Compose" to "1.1.0",
        "Kotlin Standard Library" to "1.9.22",
        "Kotlinx Coroutines" to "1.7.3",
        "Gson" to "2.10.1",
        "Coil" to "2.5.0",
        "Telephoto Zoomable Image" to "0.7.1",
        "Retrofit" to "2.9.0",
        "OkHttp" to "4.12.0",
        "DataStore" to "1.0.0",
        "AndroidX Security Crypto" to "1.1.0-alpha06",
        "Google Tink" to "1.8.0",
        "DocumentFile" to "1.0.1",
        "WebKit" to "1.9.0",
        "Palette" to "1.0.0",
        "JSoup" to "1.17.2",
        // Bundled assets
        "PDF.js" to "5.5.207",
        "Liberation Fonts" to "5.5.207",
        "Foxit / PDFium standard fonts" to "5.5.207",
        "Adobe CMap resources" to "5.5.207",
        "OpenJPEG" to "5.5.207",
        "PDF.js OpenJPEG bindings" to "5.5.207",
        "JBIG2 (PDFium)" to "5.5.207",
        "PDF.js JBIG2 bindings" to "5.5.207",
        "QCMS" to "5.5.207",
        "PDF.js QCMS bindings" to "5.5.207",
        "ICC colour profiles (iccs)" to "5.5.207",
        "Courier Prime" to "OFL 1.1",
        "JetBrains Mono" to "OFL 1.1",
        "Press Start 2P" to "OFL 1.1",
        "Share Tech Mono" to "OFL 1.1",
        "Space Mono" to "OFL 1.1",
    )

    val ALL: Set<String> get() = VERSIONS.keys
}

/**
 * A `res/raw/` text shared verbatim across many catalog entries of the same license family
 * (as opposed to a bundled asset that carries its own, entry-specific copyright line).
 */
enum class SharedLicenseText {
    APACHE_2_0,
    MIT,
    OFL_1_1,
}

/**
 * Where a catalog entry's full license text should be read from. Resolving either variant
 * needs a Context (a raw resource or an asset file); the catalog entry itself does not.
 */
sealed class LicenseTextSource {
    /** A resource in `res/raw/`, shared across every entry with the same [family]. */
    data class Shared(val family: SharedLicenseText) : LicenseTextSource()

    /** A file already bundled under `app/src/main/assets/`, read verbatim. */
    data class Asset(val path: String) : LicenseTextSource()
}

/**
 * Declarative, Context-free description of one third-party notice: its name, license
 * classification, copyright line, and where its full text lives. Deliberately excludes the
 * resolved text itself, which is the only part that needs a Context.
 */
data class LicenseEntry(
    val name: String,
    val license: LicenseType,
    val copyright: String?,
    val textSource: LicenseTextSource,
)

/**
 * The complete, Context-free catalog of third-party notices for distributed dependencies and
 * bundled assets. [OpenSourceLicenseRepository] builds its [LibraryLicense] list by mapping
 * over [ENTRIES] and resolving each entry's text; it cannot express an entry this catalog does
 * not have, and [ENTRIES] cannot name a component [OpenSourceLicenseNames.VERSIONS] doesn't
 * carry a version for, since `getAllLicenses()` looks the version up by name and would throw.
 *
 * Excludes the `RetroAchievements API` notice: it isn't a distributed dependency (no Maven
 * coordinate, no version), so it isn't tracked by [OpenSourceLicenseNames.VERSIONS] either;
 * the repository adds it separately.
 */
object OpenSourceLicenseCatalog {
    val ENTRIES: List<LicenseEntry> = listOf(
        // Gradle dependencies
        LicenseEntry(
            "AndroidX Core KTX", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "AndroidX Multidex", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Jetpack Compose BOM", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Jetpack Compose UI", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Jetpack Compose Foundation", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Jetpack Compose Material3", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Compose Material Icons Extended", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "AndroidX Lifecycle", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "AndroidX Activity Compose", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Room Database", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Dagger Hilt", LicenseType.APACHE_2_0,
            "Copyright The Dagger Authors", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Hilt Navigation Compose", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Kotlin Standard Library", LicenseType.APACHE_2_0,
            "Copyright JetBrains s.r.o. and contributors", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Kotlinx Coroutines", LicenseType.APACHE_2_0,
            "Copyright JetBrains s.r.o.", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Gson", LicenseType.APACHE_2_0,
            "Copyright Google Inc.", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Coil", LicenseType.APACHE_2_0,
            "Copyright Coil Contributors", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Telephoto Zoomable Image", LicenseType.APACHE_2_0,
            "Copyright Saket Verma", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Retrofit", LicenseType.APACHE_2_0,
            "Copyright Square, Inc.", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "OkHttp", LicenseType.APACHE_2_0,
            "Copyright Square, Inc.", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "DataStore", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "AndroidX Security Crypto", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Google Tink", LicenseType.APACHE_2_0,
            "Copyright Google Inc.", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "DocumentFile", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "WebKit", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Palette", LicenseType.APACHE_2_0,
            "Copyright The Android Open Source Project", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "JSoup", LicenseType.MIT,
            "Copyright © 2009-2024 Jonathan Hedley", LicenseTextSource.Shared(SharedLicenseText.MIT)
        ),

        // Bundled assets: PDF.js and its sub-components. Every sub-component except PDF.js
        // itself reads its own bundled license file, which carries a more accurate,
        // entry-specific copyright line than a shared template would.
        LicenseEntry(
            "PDF.js", LicenseType.APACHE_2_0,
            "Copyright Mozilla Foundation", LicenseTextSource.Shared(SharedLicenseText.APACHE_2_0)
        ),
        LicenseEntry(
            "Liberation Fonts", LicenseType.OFL_1_1,
            "Copyright 2012 Red Hat, Inc., digitized data copyright 2010 Google Corporation",
            LicenseTextSource.Asset("pdfjs/web/standard_fonts/LICENSE_LIBERATION")
        ),
        LicenseEntry(
            "Foxit / PDFium standard fonts", LicenseType.BSD_3_CLAUSE,
            "Copyright 2014 PDFium Authors",
            LicenseTextSource.Asset("pdfjs/web/standard_fonts/LICENSE_FOXIT")
        ),
        LicenseEntry(
            "Adobe CMap resources", LicenseType.BSD_3_CLAUSE,
            "Copyright 1990-2009 Adobe Systems Incorporated",
            LicenseTextSource.Asset("pdfjs/web/cmaps/LICENSE")
        ),
        LicenseEntry(
            "OpenJPEG", LicenseType.BSD_2_CLAUSE,
            "Copyright 2002-2014 Universite catholique de Louvain (UCL), Belgium and other contributors",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_OPENJPEG")
        ),
        LicenseEntry(
            "PDF.js OpenJPEG bindings", LicenseType.BSD_2_CLAUSE,
            "Copyright 2024 Mozilla Foundation",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_PDFJS_OPENJPEG")
        ),
        LicenseEntry(
            "JBIG2 (PDFium)", LicenseType.BSD_3_CLAUSE,
            "Copyright 2014 The PDFium Authors",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_JBIG2")
        ),
        LicenseEntry(
            "PDF.js JBIG2 bindings", LicenseType.APACHE_2_0,
            "Copyright 2026 Mozilla Foundation",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_PDFJS_JBIG2")
        ),
        LicenseEntry(
            "QCMS", LicenseType.MIT,
            "Copyright 2009-2024 Mozilla Corporation and 1998-2007 Marti Maria",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_QCMS")
        ),
        LicenseEntry(
            "PDF.js QCMS bindings", LicenseType.BSD_2_CLAUSE,
            "Copyright 2025 Mozilla Foundation",
            LicenseTextSource.Asset("pdfjs/web/wasm/LICENSE_PDFJS_QCMS")
        ),
        LicenseEntry(
            "ICC colour profiles (iccs)", LicenseType.CC0_1_0,
            null,
            LicenseTextSource.Asset("pdfjs/web/iccs/LICENSE")
        ),

        // Bundled assets: fonts not shipped with their own license text.
        LicenseEntry(
            "Courier Prime", LicenseType.OFL_1_1,
            "Copyright 2015 The Courier Prime Project Authors " +
                "(https://github.com/quoteunquoteapps/CourierPrime)",
            LicenseTextSource.Shared(SharedLicenseText.OFL_1_1)
        ),
        LicenseEntry(
            "JetBrains Mono", LicenseType.OFL_1_1,
            "Copyright 2020 The JetBrains Mono Project Authors " +
                "(https://github.com/JetBrains/JetBrainsMono)",
            LicenseTextSource.Shared(SharedLicenseText.OFL_1_1)
        ),
        LicenseEntry(
            "Press Start 2P", LicenseType.OFL_1_1,
            "Copyright 2012 The Press Start 2P Project Authors (cody@zone38.net), " +
                "with Reserved Font Name \"Press Start 2P\"",
            LicenseTextSource.Shared(SharedLicenseText.OFL_1_1)
        ),
        LicenseEntry(
            "Share Tech Mono", LicenseType.OFL_1_1,
            "Copyright (c) 2012, Carrois Type Design, Ralph du Carrois (post@carrois.com), " +
                "with Reserved Font Name 'Share'",
            LicenseTextSource.Shared(SharedLicenseText.OFL_1_1)
        ),
        LicenseEntry(
            "Space Mono", LicenseType.OFL_1_1,
            "Copyright 2016 The Space Mono Project Authors " +
                "(https://github.com/googlefonts/spacemono)",
            LicenseTextSource.Shared(SharedLicenseText.OFL_1_1)
        ),
    )

    val NAMES: Set<String> get() = ENTRIES.map { it.name }.toSet()
}
