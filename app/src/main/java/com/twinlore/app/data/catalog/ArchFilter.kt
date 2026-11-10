package com.twinlore.app.data.catalog

import android.os.Build

object ArchFilter {

    private val ARCH_PATTERNS = mapOf(
        "arm64-v8a" to listOf("arm64", "aarch64", "arm64-v8a"),
        "armeabi-v7a" to listOf("armeabi", "armv7", "arm-v7a"),
        "x86_64" to listOf("x86_64", "x86-64"),
        "x86" to listOf("x86")
    )

    /** Every known arch substring, used to detect whether filenames encode arch at all. */
    private val ALL_ARCH_TOKENS: Set<String> =
        ARCH_PATTERNS.values.flatten().toSet()

    /**
     * Filters [fileNames] to those matching the device's supported ABIs.
     *
     * ABIs are tried in priority order (first = most preferred). As soon as
     * any ABI produces at least one match the result is returned, so an arm64
     * device gets arm64 APKs rather than armeabi-v7a fallbacks.
     *
     * Files containing "universal" in their name are always included.
     *
     * If no filename contains any known arch substring the full list is returned
     * (assumes universal builds that do not encode arch in the filename).
     */
    fun filter(
        fileNames: List<String>,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS
    ): List<String> {
        if (fileNames.isEmpty()) return emptyList()

        val lowered = fileNames.map { it.lowercase() }

        // If none of the filenames contain any arch token, return everything.
        val hasAnyArch = lowered.any { name ->
            ALL_ARCH_TOKENS.any { name.contains(it) }
        }
        if (!hasAnyArch) return fileNames

        // Universals are always kept.
        val universals = fileNames.filterIndexed { i, _ -> lowered[i].contains("universal") }

        // Try each ABI in priority order; return the first that matches.
        for (abi in supportedAbis) {
            val patterns = ARCH_PATTERNS[abi] ?: continue
            val matches = fileNames.filterIndexed { i, _ ->
                val lower = lowered[i]
                patterns.any { lower.contains(it) }
            }
            if (matches.isNotEmpty()) {
                return (universals + matches).distinct()
            }
        }

        // No ABI matched but arch tokens exist: return universals if any.
        return universals.ifEmpty { fileNames }
    }
}
