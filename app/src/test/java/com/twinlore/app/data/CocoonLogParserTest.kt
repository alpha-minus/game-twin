package com.twinlore.app.data

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class CocoonLogParserTest {

    private val parser = CocoonLogParser(mock(Context::class.java))

    @Test
    fun `parseBlock extracts RetroArch launch with core name`() {
        val lines = listOf(
            "[2026-05-16 07:03:22.117] [----] \u2550\u2550\u2550\u2550\u2550\u2550 Launch: Super Mario Advance 4 \u2550\u2550\u2550\u2550\u2550\u2550",
            "[2026-05-16 07:03:22.118] [INFO] Game: Super Mario Advance 4 | File: Super Mario Advance 4.zip",
            "[2026-05-16 07:03:22.119] [INFO] Player: RetroArch - vba_next (com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture)",
            "[2026-05-16 07:03:22.124] [INFO] Resolved file: /storage/emulated/0/ROMS/GBA/Super Mario Advance 4.zip (exists=true, hasRealPath=true)",
            "[2026-05-16 07:03:22.126] [INFO] {file.path} \u2192 /storage/emulated/0/ROMS/GBA/Super Mario Advance 4.zip",
            "[2026-05-16 07:03:22.141] [INFO] Intent: action=android.intent.action.VIEW, data=null, component=ComponentInfo{com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture}",
            "[2026-05-16 07:03:22.143] [INFO] Intent extras: DATADIR, SDCARD, EXTERNAL, APK, IME, ROM, CONFIGFILE, LIBRETRO",
            "[2026-05-16 07:03:22.162] [INFO] Launch SUCCESS \u2014 taskId=null"
        )
        val result = parser.parseBlock(lines)
        assertNotNull(result)
        assertEquals("Super Mario Advance 4", result!!.gameName)
        assertEquals("Super Mario Advance 4.zip", result.romFilename)
        assertEquals("/storage/emulated/0/ROMS/GBA/Super Mario Advance 4.zip", result.romPath)
        assertEquals("vba_next", result.coreName)
        assertEquals("com.retroarch", result.packageName)
        assertNotNull(result.launchIntent)
        assertEquals("com.retroarch", result.launchIntent!!.packageName)
        assertNull(result.launchIntent!!.action)
        assertNull(result.launchIntent!!.dataUri)
        assertEquals("string", result.launchIntent!!.extras["ROM"]?.type)
    }

    @Test
    fun `parseBlock extracts standalone emulator launch from Intent line`() {
        val lines = listOf(
            "[2026-05-16 07:28:30.997] [----] \u2550\u2550\u2550\u2550\u2550\u2550 Launch: Axiom Verge \u2550\u2550\u2550\u2550\u2550\u2550",
            "[2026-05-16 07:28:30.999] [INFO] Game: Axiom Verge | File: Axiom Verge (USA) (v16) (1).wua",
            "[2026-05-16 07:28:31.001] [INFO] Player: CEMU (info.cemu.cemu/.emulation.EmulationActivity)",
            "[2026-05-16 07:28:31.006] [INFO] Resolved file: /storage/emulated/0/ROMS/WiiU/Axiom Verge (USA) (v16) (1).wua (exists=true, hasRealPath=true)",
            "[2026-05-16 07:28:31.009] [INFO] {file.path} \u2192 /storage/emulated/0/ROMS/WiiU/Axiom Verge (USA) (v16) (1).wua",
            "[2026-05-16 07:28:31.017] [INFO] Intent: action=android.intent.action.VIEW, data=content://com.android.externalstorage.documents/tree/primary%3AROMS/document/primary%3AROMS%2FWiiU%2FAxiom%20Verge.wua, component=ComponentInfo{info.cemu.cemu/info.cemu.cemu.emulation.EmulationActivity}",
            "[2026-05-16 07:28:31.018] [INFO] Intent extras: none",
            "[2026-05-16 07:28:31.044] [INFO] Launch SUCCESS \u2014 taskId=null"
        )
        val result = parser.parseBlock(lines)
        assertNotNull(result)
        assertEquals("Axiom Verge", result!!.gameName)
        assertEquals("Axiom Verge (USA) (v16) (1).wua", result.romFilename)
        assertEquals("/storage/emulated/0/ROMS/WiiU/Axiom Verge (USA) (v16) (1).wua", result.romPath)
        assertNull(result.coreName)
        assertEquals("info.cemu.cemu", result.packageName)
        assertNotNull(result.launchIntent)
        assertEquals("info.cemu.cemu", result.launchIntent!!.packageName)
        assertEquals("info.cemu.cemu.emulation.EmulationActivity", result.launchIntent!!.activityClass)
        assertEquals("android.intent.action.VIEW", result.launchIntent!!.action)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AROMS/document/primary%3AROMS%2FWiiU%2FAxiom%20Verge.wua", result.launchIntent!!.dataUri)
        assertTrue(result.launchIntent!!.extras.isEmpty())
    }

    @Test
    fun `parseBlock returns null when no game name`() {
        val lines = listOf(
            "[2026-05-16 07:01:45.301] [INFO] Player: CEMU (info.cemu.cemu/.emulation.EmulationActivity)"
        )
        val result = parser.parseBlock(lines)
        assertNull(result)
    }

    // ---- block splitting / last-played resolution -------------------------

    /**
     * Twelve-line blocks, matching the shape Cocoon writes on the AYN Thor.
     * The length matters: the old fixed 20-line window reached eight lines into
     * the following block, which is how the next block's `Game:` line won.
     */
    private fun block(kind: String, game: String, file: String, core: String, time: String, success: Boolean) = listOf(
        "[$time.117] [----] ══════ $kind: $game ══════",
        "[$time.118] [INFO] Game: $game | File: $file",
        "[$time.119] [INFO] Player: RetroArch - $core (com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture)",
        "[$time.120] [INFO] Emulator installed: true",
        "[$time.121] [INFO] Storage permission: granted",
        "[$time.124] [INFO] Resolved file: /storage/emulated/0/ROMS/GBA/$file (exists=true, hasRealPath=true)",
        "[$time.126] [INFO] {file.path} → /storage/emulated/0/ROMS/GBA/$file",
        "[$time.130] [INFO] Config written: /storage/emulated/0/Cocoon/retroarch.cfg",
        "[$time.141] [INFO] Intent: action=android.intent.action.VIEW, data=null, component=ComponentInfo{com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture}",
        "[$time.143] [INFO] Intent extras: DATADIR, SDCARD, EXTERNAL, APK, IME, ROM, CONFIGFILE, LIBRETRO",
        "[$time.150] [INFO] Starting activity"
    ) + if (success) listOf("[$time.162] [INFO] $kind SUCCESS — taskId=null") else listOf("[$time.162] [WARN] $kind aborted")

    private fun launchBlock(game: String, file: String, core: String, time: String, success: Boolean = true) =
        block("Launch", game, file, core, time, success)

    private fun swapBlock(game: String, file: String, core: String, time: String, success: Boolean = true) =
        block("Swap", game, file, core, time, success)

    /**
     * The user's bug, reproduced from their AYN Thor log: the last Launch is
     * Metroid Fusion, followed by Swaps to Metroid Zero Mission and Zelda.
     * The genuinely last-played game is the Zelda swap.
     */
    @Test
    fun `swap blocks after the last launch win over the launch`() {
        val log =
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02") +
            swapBlock("Metroid Zero Mission", "Metroid_ Zero Mission.zip", "vba_next", "2026-08-28 00:09:47") +
            swapBlock(
                "Legend of Zelda, The - A Link to the Past & Four Swords",
                "Legend of Zelda, The - A Link to the Past & Four Swords.zip",
                "vba_next",
                "2026-08-28 00:10:48"
            )

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Legend of Zelda, The - A Link to the Past & Four Swords", result!!.gameName)
        assertEquals("Legend of Zelda, The - A Link to the Past & Four Swords.zip", result.romFilename)
    }

    @Test
    fun `a single swap after a launch resolves to the swapped game`() {
        val log =
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02") +
            swapBlock("Metroid Zero Mission", "Metroid_ Zero Mission.zip", "vba_next", "2026-08-28 00:09:47")

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Metroid Zero Mission", result!!.gameName)
        assertEquals("Metroid_ Zero Mission.zip", result.romFilename)
    }

    @Test
    fun `a trailing launch block still resolves to its own game`() {
        val log =
            swapBlock("Metroid Zero Mission", "Metroid_ Zero Mission.zip", "vba_next", "2026-08-28 00:09:47") +
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:11:30")

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Metroid Fusion", result!!.gameName)
        assertEquals("Metroid Fusion.zip", result.romFilename)
        assertEquals("vba_next", result.coreName)
        assertEquals("com.retroarch", result.packageName)
    }

    @Test
    fun `a block longer than twenty lines still parses`() {
        val noise = (1..25).map { "[2026-08-28 00:07:03.$it] [INFO] Preflight check $it passed" }
        val block = launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02")
        // Push the success marker and the intent well past the old 20-line window.
        val log = block.take(2) + noise + block.drop(2)

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Metroid Fusion", result!!.gameName)
        assertEquals("/storage/emulated/0/ROMS/GBA/Metroid Fusion.zip", result.romPath)
        assertEquals("vba_next", result.coreName)
        assertNotNull(result.launchIntent)
    }

    @Test
    fun `a trailing block without a success marker falls back to the last successful one`() {
        val log =
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02") +
            swapBlock("Metroid Zero Mission", "Metroid_ Zero Mission.zip", "vba_next", "2026-08-28 00:09:47") +
            swapBlock("Kirby & The Amazing Mirror", "Kirby.zip", "vba_next", "2026-08-28 00:10:48", success = false)

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Metroid Zero Mission", result!!.gameName)
    }

    @Test
    fun `a failed trailing launch falls back to the last successful launch`() {
        val log = launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02") +
            launchBlock("Kirby & The Amazing Mirror", "Kirby.zip", "vba_next", "2026-08-28 00:10:48", success = false)

        val result = parser.findLastLaunchedGame(log)
        assertNotNull(result)
        assertEquals("Metroid Fusion", result!!.gameName)
    }

    /**
     * Field bleed: a block bounded by a fixed line count swallowed the head of
     * the following block, and parseBlock's last-match-wins overwrote the game.
     */
    @Test
    fun `a launch block does not inherit the next block's game line`() {
        val log =
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02") +
            swapBlock("Metroid Zero Mission", "Metroid_ Zero Mission.zip", "vba_next", "2026-08-28 00:09:47")

        val blocks = parser.splitBlocks(log)
        assertEquals(2, blocks.size)
        assertEquals(12, blocks[0].size)
        assertFalse(
            "First block leaked lines from the following Swap block: ${blocks[0]}",
            blocks[0].any { it.contains("Metroid Zero Mission") }
        )

        val first = parser.parseBlock(blocks[0])
        assertNotNull(first)
        assertEquals("Metroid Fusion", first!!.gameName)
        assertEquals("Metroid Fusion.zip", first.romFilename)
    }

    @Test
    fun `splitBlocks bounds the last block at end of file`() {
        val log = listOf("[2026-08-28 00:07:00.000] [INFO] Cocoon started") +
            launchBlock("Metroid Fusion", "Metroid Fusion.zip", "vba_next", "2026-08-28 00:07:02")

        val blocks = parser.splitBlocks(log)
        assertEquals(1, blocks.size)
        assertEquals(12, blocks[0].size)
        assertTrue(blocks[0].first().contains("Launch: Metroid Fusion"))
        assertTrue(blocks[0].last().contains("Launch SUCCESS"))
    }

    @Test
    fun `a log with no launch or swap headers resolves to null`() {
        val log = listOf(
            "[2026-08-28 00:07:00.000] [INFO] Cocoon started",
            "[2026-08-28 00:07:01.000] [INFO] Scanning ROM folders"
        )
        assertNull(parser.findLastLaunchedGame(log))
        assertTrue(parser.splitBlocks(log).isEmpty())
    }

    @Test
    fun `parseBlock extracts the game name from a swap header alone`() {
        val lines = listOf(
            "[2026-08-28 00:09:47.117] [----] ══════ Swap: Metroid Zero Mission ══════",
            "[2026-08-28 00:09:47.119] [INFO] Player: RetroArch - vba_next (com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture)",
            "[2026-08-28 00:09:47.162] [INFO] Swap SUCCESS — taskId=null"
        )
        val result = parser.parseBlock(lines)
        assertNotNull(result)
        assertEquals("Metroid Zero Mission", result!!.gameName)
    }
}
