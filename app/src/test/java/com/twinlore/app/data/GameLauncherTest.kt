package com.twinlore.app.data

import org.junit.Assert.*
import org.junit.Test

class GameLauncherTest {

    @Test
    fun `derivePackageFromCorePath returns package from data-data path`() {
        val result = GameLauncher.derivePackageFromCorePath(
            "/data/data/com.retroarch.ra32/cores/mgba_libretro_android.so"
        )
        assertEquals("com.retroarch.ra32", result)
    }

    @Test
    fun `derivePackageFromCorePath returns package from data-user path`() {
        val result = GameLauncher.derivePackageFromCorePath(
            "/data/user/0/com.retroarch/cores/snes9x_libretro_android.so"
        )
        assertEquals("com.retroarch", result)
    }

    @Test
    fun `derivePackageFromCorePath returns null for unrecognized path`() {
        val result = GameLauncher.derivePackageFromCorePath("/storage/emulated/0/cores/mgba.so")
        assertNull(result)
    }

    @Test
    fun `derivePackageFromCorePath returns null for blank input`() {
        assertNull(GameLauncher.derivePackageFromCorePath(null))
        assertNull(GameLauncher.derivePackageFromCorePath(""))
        assertNull(GameLauncher.derivePackageFromCorePath("   "))
    }

    @Test
    fun `buildRetroArchIntent creates correct intent structure`() {
        val intent = GameLauncher.buildRetroArchIntent(
            romPath = "/storage/emulated/0/ROMS/GBA/game.zip",
            corePath = "/data/data/com.retroarch.ra32/cores/mgba_libretro_android.so",
            emulatorPackage = "com.retroarch.ra32",
            configPath = "/storage/emulated/0/Android/data/com.retroarch.ra32/files/retroarch.cfg"
        )
        assertEquals("com.retroarch.ra32", intent.packageName)
        assertEquals("com.retroarch.ra32.browser.retroactivity.RetroActivityFuture", intent.activityClass)
        assertNull(intent.action)
        assertNull(intent.dataUri)
        assertEquals("/storage/emulated/0/ROMS/GBA/game.zip", intent.extras["ROM"]?.value)
        assertEquals("string", intent.extras["ROM"]?.type)
        assertEquals("/data/data/com.retroarch.ra32/cores/mgba_libretro_android.so", intent.extras["LIBRETRO"]?.value)
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.ra32/files/retroarch.cfg", intent.extras["CONFIGFILE"]?.value)
        assertEquals("/storage/emulated/0/ROMS/GBA/game.zip", intent.romPath)
    }

    @Test
    fun `buildRetroArchIntent uses default config path when null`() {
        val intent = GameLauncher.buildRetroArchIntent(
            romPath = "/storage/emulated/0/ROMS/GBA/game.zip",
            corePath = "/data/data/com.retroarch.ra32/cores/mgba_libretro_android.so",
            emulatorPackage = "com.retroarch.ra32",
            configPath = null
        )
        assertEquals(
            "/storage/emulated/0/Android/data/com.retroarch.ra32/files/retroarch.cfg",
            intent.extras["CONFIGFILE"]?.value
        )
    }

    @Test
    fun `buildRetroArchIntent produces valid JSON roundtrip`() {
        val intent = GameLauncher.buildRetroArchIntent(
            romPath = "/storage/emulated/0/ROMS/GBA/game.zip",
            corePath = "/data/data/com.retroarch.ra32/cores/mgba_libretro_android.so",
            emulatorPackage = "com.retroarch.ra32",
            configPath = null
        )
        val json = intent.toJson()
        val roundtripped = ESDELogParser.ParsedLaunchIntent.fromJson(json)
        assertNotNull(roundtripped)
        assertEquals(intent.packageName, roundtripped!!.packageName)
        assertEquals(intent.activityClass, roundtripped.activityClass)
        assertEquals(intent.extras["ROM"]?.value, roundtripped.extras["ROM"]?.value)
        assertEquals(intent.extras["LIBRETRO"]?.value, roundtripped.extras["LIBRETRO"]?.value)
    }

    @Test
    fun `ParsedLaunchIntent toJson includes action and dataUri`() {
        val intent = ESDELogParser.ParsedLaunchIntent(
            packageName = "info.cemu.cemu",
            activityClass = "info.cemu.cemu.emulation.EmulationActivity",
            action = "android.intent.action.VIEW",
            dataUri = "content://com.android.externalstorage.documents/tree/primary%3AROMS/document/primary%3AROMS%2FWiiU%2Fgame.wua",
            extras = mapOf(
                "ROM" to ESDELogParser.ParsedLaunchIntent.TypedExtra("string", "/storage/emulated/0/ROMS/WiiU/game.wua")
            ),
            romPath = "/storage/emulated/0/ROMS/WiiU/game.wua"
        )
        val json = intent.toJson()
        val parsed = ESDELogParser.ParsedLaunchIntent.fromJson(json)
        assertNotNull(parsed)
        assertEquals("android.intent.action.VIEW", parsed!!.action)
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AROMS/document/primary%3AROMS%2FWiiU%2Fgame.wua", parsed.dataUri)
        assertEquals("string", parsed.extras["ROM"]?.type)
        assertEquals("/storage/emulated/0/ROMS/WiiU/game.wua", parsed.extras["ROM"]?.value)
    }

    @Test
    fun `ParsedLaunchIntent toJson with integer extras roundtrips`() {
        val intent = ESDELogParser.ParsedLaunchIntent(
            packageName = "app.gamenative",
            activityClass = ".MainActivity",
            action = "app.gamenative.LAUNCH_GAME",
            dataUri = null,
            extras = mapOf(
                "app_id" to ESDELogParser.ParsedLaunchIntent.TypedExtra("int", "1030300")
            ),
            romPath = null
        )
        val json = intent.toJson()
        val parsed = ESDELogParser.ParsedLaunchIntent.fromJson(json)
        assertNotNull(parsed)
        assertEquals("app.gamenative.LAUNCH_GAME", parsed!!.action)
        assertNull(parsed.dataUri)
        assertEquals("int", parsed.extras["app_id"]?.type)
        assertEquals("1030300", parsed.extras["app_id"]?.value)
    }

    @Test
    fun `ParsedLaunchIntent fromJson with null action and dataUri`() {
        val json = """{"pkg":"com.retroarch","act":"com.retroarch.browser.retroactivity.RetroActivityFuture","extras":{"ROM":{"type":"string","value":"/path/game.zip"},"LIBRETRO":{"type":"string","value":"/data/data/com.retroarch/cores/mgba_libretro_android.so"}}}"""
        val parsed = ESDELogParser.ParsedLaunchIntent.fromJson(json)
        assertNotNull(parsed)
        assertNull(parsed!!.action)
        assertNull(parsed.dataUri)
        assertEquals("com.retroarch", parsed.packageName)
        assertEquals("/path/game.zip", parsed.extras["ROM"]?.value)
    }

    @Test
    fun `ParsedLaunchIntent fromJson handles legacy plain-string extras`() {
        // Old format stored extras as {"key": "value"} instead of {"key": {"type":"string","value":"..."}}
        val json = """{"pkg":"com.retroarch","act":"com.retroarch.browser.retroactivity.RetroActivityFuture","extras":{"ROM":"/storage/emulated/0/game.zip","LIBRETRO":"/data/data/com.retroarch/cores/mgba.so","CONFIGFILE":"/storage/emulated/0/retroarch.cfg"}}"""
        val parsed = ESDELogParser.ParsedLaunchIntent.fromJson(json)
        assertNotNull(parsed)
        assertEquals("com.retroarch", parsed!!.packageName)
        assertEquals("/storage/emulated/0/game.zip", parsed.extras["ROM"]?.value)
        assertEquals("string", parsed.extras["ROM"]?.type)
        assertEquals("/data/data/com.retroarch/cores/mgba.so", parsed.extras["LIBRETRO"]?.value)
    }
}
