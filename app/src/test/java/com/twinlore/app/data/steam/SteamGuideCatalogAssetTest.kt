package com.twinlore.app.data.steam

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SteamGuideCatalogAssetTest {
    private val assetText: String =
        File("src/main/assets/steam_guides_catalog.json").readText()

    @Test fun asset_parses_into_entries() {
        val entries = SteamGuideCatalog.parse(assetText)
        assertTrue("expected a healthy number of curated guides", entries.size >= 40)
    }

    @Test fun every_entry_is_well_formed() {
        val entries = SteamGuideCatalog.parse(assetText)
        entries.forEach { e ->
            assertTrue("blank title", e.title.isNotBlank())
            assertTrue("guideId must be numeric: ${e.title}", e.guideId.isNotEmpty() && e.guideId.all { it.isDigit() })
        }
    }

    @Test fun titles_are_unique() {
        val entries = SteamGuideCatalog.parse(assetText)
        val dupes = entries.groupBy { it.title.lowercase() }.filter { it.value.size > 1 }.keys
        assertTrue("duplicate titles: $dupes", dupes.isEmpty())
    }
}
