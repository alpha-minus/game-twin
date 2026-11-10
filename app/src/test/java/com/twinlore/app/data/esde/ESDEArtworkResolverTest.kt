package com.twinlore.app.data.esde

import org.junit.Assert.*
import org.junit.Test

class ESDEArtworkResolverTest {
    @Test
    fun `candidate paths use system, media folder and rom name without extension`() {
        val map = ESDEArtworkResolver.candidateRelativePaths(
            "snes", "/storage/emulated/0/ROMs/snes/Chrono Trigger.sfc"
        )
        val hero = map[MediaSlot.HERO]!!
        assertTrue(hero.contains("downloaded_media/snes/fanart/Chrono Trigger.png"))
        assertTrue(hero.contains("downloaded_media/snes/fanart/Chrono Trigger.jpg"))
        assertTrue(hero.contains("downloaded_media/snes/fanart/Chrono Trigger.webp"))
        assertTrue(map[MediaSlot.LOGO]!!.first().contains("/marquees/"))
        assertTrue(map[MediaSlot.MIX]!!.first().contains("/miximages/"))
    }

    @Test
    fun `rom name strips only the final extension`() {
        val map = ESDEArtworkResolver.candidateRelativePaths(
            "psx", "/roms/psx/Final Fantasy VII (Disc 1).chd"
        )
        assertTrue(map[MediaSlot.BOX_FRONT]!!.first().endsWith("Final Fantasy VII (Disc 1).png"))
    }

    @Test
    fun `rom name without extension keeps dotless basenames intact`() {
        assertEquals("Sonic", ESDEArtworkResolver.romNameWithoutExt("/roms/md/Sonic"))
        assertEquals("Chrono Trigger", ESDEArtworkResolver.romNameWithoutExt("/a/Chrono Trigger.sfc"))
    }

    @Test
    fun `flat rom yields only the bare name`() {
        assertEquals(
            listOf("Strider 2 (USA)"),
            ESDEArtworkResolver.mediaNameCandidates(
                "psx", "/storage/emulated/0/ROMs/psx/Strider 2 (USA).bin"
            ),
        )
    }

    @Test
    fun `multi-disc rom in a game folder yields subpath, folder, then bare name`() {
        assertEquals(
            listOf(
                "Castlevania Symphony of the Night/Castlevania - Symphony of the Night (USA)",
                "Castlevania Symphony of the Night",
                "Castlevania - Symphony of the Night (USA)",
            ),
            ESDEArtworkResolver.mediaNameCandidates(
                "psx",
                "/storage/emulated/0/ROMs/psx/Castlevania Symphony of the Night/" +
                    "Castlevania - Symphony of the Night (USA).cue"
            ),
        )
    }

    @Test
    fun `system segment match is case-insensitive and uses the last occurrence`() {
        assertEquals(
            listOf("Metal Slug X/Metal Slug X (USA)", "Metal Slug X", "Metal Slug X (USA)"),
            ESDEArtworkResolver.mediaNameCandidates(
                "PSX", "/roms/psx/Metal Slug X/Metal Slug X (USA).cue"
            ),
        )
    }

    @Test
    fun `rom path without a system segment still tries the parent folder`() {
        // The parent directory may be a multi-disc game folder; the extra candidates
        // just probe and miss when it isn't.
        assertEquals(
            listOf("place/Some Game", "place", "Some Game"),
            ESDEArtworkResolver.mediaNameCandidates("psx", "/weird/place/Some Game.chd"),
        )
    }

    @Test
    fun `gamelist-relative multi-disc path yields the game-folder candidates`() {
        // Gamelist entries are written relative ("./Game Dir/Disc.bin") with no system
        // segment — the stats screen feeds these, and multi-disc art lives under the dir name.
        assertEquals(
            listOf(
                "Castlevania Symphony of the Night/Castlevania - Symphony of the Night (USA) (Track 1)",
                "Castlevania Symphony of the Night",
                "Castlevania - Symphony of the Night (USA) (Track 1)",
            ),
            ESDEArtworkResolver.mediaNameCandidates(
                "psx",
                "./Castlevania Symphony of the Night/" +
                    "Castlevania - Symphony of the Night (USA) (Track 1).bin"
            ),
        )
        assertEquals(
            listOf("Strider 2 (USA)"),
            ESDEArtworkResolver.mediaNameCandidates("psx", "./Strider 2 (USA).bin"),
        )
    }

    @Test
    fun `candidate paths include the game-folder variants for multi-disc roms`() {
        val map = ESDEArtworkResolver.candidateRelativePaths(
            "psx", "/roms/psx/Metal Slug X/Metal Slug X (USA).cue"
        )
        val covers = map[MediaSlot.BOX_FRONT]!!
        assertTrue(covers.contains("downloaded_media/psx/covers/Metal Slug X/Metal Slug X (USA).png"))
        assertTrue(covers.contains("downloaded_media/psx/covers/Metal Slug X.png"))
        assertTrue(covers.contains("downloaded_media/psx/covers/Metal Slug X (USA).png"))
        // Most specific candidate (the per-file entry) probes first.
        assertTrue(
            covers.indexOf("downloaded_media/psx/covers/Metal Slug X/Metal Slug X (USA).png") <
                covers.indexOf("downloaded_media/psx/covers/Metal Slug X.png")
        )
    }
}
