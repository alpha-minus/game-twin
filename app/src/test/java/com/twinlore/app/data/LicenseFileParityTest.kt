package com.twinlore.app.data

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `THIRD_PARTY_NOTICES.txt` and the in-app notice list are two hand-maintained copies of the same
 * facts, and they had already drifted (the file was missing Hilt Navigation Compose). This keeps them
 * honest without forcing either to be generated from the other.
 */
class LicenseFileParityTest {

    @Test
    fun `every in-app notice also appears in the third-party notices file`() {
        val part2 = RepoFiles.at("THIRD_PARTY_NOTICES.txt").readText()
            .substringAfter("THIRD-PARTY OPEN-SOURCE NOTICES")
        assertTrue("THIRD_PARTY_NOTICES.txt has no notices section", part2.isNotBlank())

        // Pinning name and version together (rather than a bare name.contains) closes two holes:
        // a name that is a substring of another entry's line (e.g. "OpenJPEG" inside "PDF.js
        // OpenJPEG bindings") would otherwise hide that entry's own line going missing entirely,
        // and a version could drift wrong while the bare name kept the assertion green.
        val missing = OpenSourceLicenseNames.ALL.filterNot { name ->
            val version = OpenSourceLicenseNames.VERSIONS.getValue(name)
            part2.contains("- $name ($version)")
        }
        assertTrue("missing from THIRD_PARTY_NOTICES.txt: ${missing.joinToString()}", missing.isEmpty())
    }
}
