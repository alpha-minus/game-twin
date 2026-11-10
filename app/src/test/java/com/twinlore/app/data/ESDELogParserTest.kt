package com.twinlore.app.data

import org.junit.Assert.*
import org.junit.Test

class ESDELogParserTest {

    @Test
    fun `parseLaunchCommandFromLines parses RetroArch intent with string extras`() {
        val lines = listOf(
            """May 16 06:47:48 Info:   Launching game "Strider 2 (USA)" from system "Sony PlayStation (psx)"...""",
            "May 16 06:47:48 Info:   Expanded emulator launch arguments:",
            "May 16 06:47:48 Info:   Package: com.retroarch",
            "May 16 06:47:48 Info:   Activity: com.retroarch.browser.retroactivity.RetroActivityFuture",
            "May 16 06:47:48 Info:   Extra name: CONFIGFILE",
            "May 16 06:47:48 Info:   Extra value: /storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg",
            "May 16 06:47:48 Info:   Extra name: LIBRETRO",
            "May 16 06:47:48 Info:   Extra value: /data/data/com.retroarch/cores/mednafen_psx_libretro_android.so",
            "May 16 06:47:48 Info:   Extra name: ROM",
            "May 16 06:47:48 Info:   Extra value: %ROM%",
            "May 16 06:47:48 Info:   %ROM% expanded: /storage/emulated/0/ROMs/psx/Strider 2 (USA).bin"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNotNull(result)
        assertEquals("com.retroarch", result!!.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", result.activityClass)
        assertNull(result.action)
        assertNull(result.dataUri)
        assertEquals("/storage/emulated/0/ROMs/psx/Strider 2 (USA).bin", result.extras["ROM"]?.value)
        assertEquals("string", result.extras["ROM"]?.type)
        assertEquals("string", result.extras["LIBRETRO"]?.type)
        assertEquals("/storage/emulated/0/ROMs/psx/Strider 2 (USA).bin", result.romPath)
    }

    @Test
    fun `parseLaunchCommandFromLines parses standalone emulator with action and data`() {
        val lines = listOf(
            """May 16 06:46:41 Info:   Launching game "Homebrew Demo" from system "Nintendo Switch (switch)"...""",
            "May 16 06:46:41 Info:   Expanded emulator launch arguments:",
            "May 16 06:46:41 Info:   Package: dev.eden.eden_emulator",
            "May 16 06:46:41 Info:   Activity: org.yuzu.yuzu_emu.activities.EmulationActivity",
            "May 16 06:46:41 Info:   Action: android.nfc.action.TECH_DISCOVERED",
            "May 16 06:46:41 Info:   Data: %ROMPROVIDER%",
            "May 16 06:46:41 Info:   %ROMPROVIDER% expanded: content://org.es_de.frontend.files/external/ROMs/switch/Homebrew%20Demo.nro"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNotNull(result)
        assertEquals("dev.eden.eden_emulator", result!!.packageName)
        assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", result.activityClass)
        assertEquals("android.nfc.action.TECH_DISCOVERED", result.action)
        assertEquals("content://org.es_de.frontend.files/external/ROMs/switch/Homebrew%20Demo.nro", result.dataUri)
        assertTrue(result.extras.isEmpty())
        // romPath derived from ES-DE FileProvider URI
        assertEquals("/storage/emulated/0/ROMs/switch/Homebrew Demo.nro", result.romPath)
    }

    @Test
    fun `parseLaunchCommandFromLines parses steam launcher with action and integer extras`() {
        val lines = listOf(
            """May 16 06:49:44 Info:   Launching game "Hollow Knight: Silksong" from system "Valve Steam (steam)"...""",
            "May 16 06:49:44 Info:   Expanded emulator launch arguments:",
            "May 16 06:49:44 Info:   Package: app.gamenative",
            "May 16 06:49:44 Info:   Activity: .MainActivity",
            "May 16 06:49:44 Info:   Action: app.gamenative.LAUNCH_GAME",
            "May 16 06:49:44 Info:   Extra integer name: app_id",
            "May 16 06:49:44 Info:   Extra integer value: 1030300"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNotNull(result)
        assertEquals("app.gamenative", result!!.packageName)
        assertEquals(".MainActivity", result.activityClass)
        assertEquals("app.gamenative.LAUNCH_GAME", result.action)
        assertNull(result.dataUri)
        assertEquals("int", result.extras["app_id"]?.type)
        assertEquals("1030300", result.extras["app_id"]?.value)
    }

    @Test
    fun `parseLaunchCommandFromLines parses ROMSAF data placeholder`() {
        val lines = listOf(
            """May 16 19:18:13 Info:   Launching game "Burnout Legends" from system "Sony PlayStation Portable (psp)"...""",
            "May 16 19:18:13 Info:   Expanded emulator launch arguments:",
            "May 16 19:18:13 Info:   Package: org.ppsspp.ppsspp",
            "May 16 19:18:13 Info:   Activity: .PpssppActivity",
            "May 16 19:18:13 Info:   Action: android.intent.action.VIEW",
            "May 16 19:18:13 Info:   Data: %ROMSAF%",
            "May 16 19:18:13 Info:   %ROMSAF% expanded: content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fpsp/document/primary%3AROMs%2Fpsp%2FBurnout%20Legends.chd"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNotNull(result)
        assertEquals("org.ppsspp.ppsspp", result!!.packageName)
        assertEquals(".PpssppActivity", result.activityClass)
        assertEquals("android.intent.action.VIEW", result.action)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fpsp/document/primary%3AROMs%2Fpsp%2FBurnout%20Legends.chd", result.dataUri)
        assertTrue(result.extras.isEmpty())
        // romPath derived from SAF document URI
        assertEquals("/storage/emulated/0/ROMs/psp/Burnout Legends.chd", result.romPath)
    }

    @Test
    fun `parseLaunchCommandFromLines resolves placeholder in extra value not dataUri`() {
        val lines = listOf(
            """May 17 07:40:03 Info:   Launching game "Castlevania" from system "Nintendo DS (nds)"...""",
            "May 17 07:40:03 Info:   Expanded emulator launch arguments:",
            "May 17 07:40:03 Info:   Package: me.magnum.melonds.nightly",
            "May 17 07:40:03 Info:   Activity: me.magnum.melonds.ui.emulator.EmulatorActivity",
            "May 17 07:40:03 Info:   Action: me.magnum.melonds.nightly.LAUNCH_ROM",
            "May 17 07:40:03 Info:   Extra name: uri",
            "May 17 07:40:03 Info:   Extra value: %ROMSAF%",
            "May 17 07:40:03 Info:   %ROMSAF% expanded: content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fnds/document/primary%3AROMs%2Fnds%2FCastlevania.zip"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNotNull(result)
        assertEquals("me.magnum.melonds.nightly", result!!.packageName)
        assertEquals("me.magnum.melonds.nightly.LAUNCH_ROM", result.action)
        assertNull(result.dataUri)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fnds/document/primary%3AROMs%2Fnds%2FCastlevania.zip", result.extras["uri"]?.value)
        // romPath derived from the URI in the extra
        assertEquals("/storage/emulated/0/ROMs/nds/Castlevania.zip", result.romPath)
    }

    @Test
    fun `parseLaunchCommandFromLines returns null when package missing`() {
        val lines = listOf(
            "May 16 06:46:41 Info:   Activity: org.yuzu.yuzu_emu.activities.EmulationActivity"
        )
        val result = ESDELogParser.parseLaunchCommandFromLines(lines)
        assertNull(result)
    }
}
