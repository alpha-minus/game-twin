package com.twinlore.app

import java.io.File

/**
 * Locates files relative to the repository root by walking up from the unit-test working
 * directory. Gradle sets that directory to the module dir by default, but these tests read files
 * both inside the module (assets) and outside it (LICENSE), so anchoring on a marker file is
 * sturdier than assuming either one.
 */
object RepoFiles {
    val root: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "repo root not found above ${File("").absolutePath}" }
    }

    fun at(relative: String): File = File(root, relative)

    fun asset(relative: String): File = at("app/src/main/assets/$relative")
}
