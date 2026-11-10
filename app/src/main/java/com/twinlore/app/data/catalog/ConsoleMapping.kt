package com.twinlore.app.data.catalog

/**
 * Short console / platform labels keyed by Android package id. Used as the
 * launcher's Emulators-section subtitle.
 *
 * The upstream Obtainium pack ships descriptive sentences in its `about`
 * field (e.g. "PPSSPP is an open source, fast and portable PSP emulator") —
 * fine for the catalog screen but too long for the small cell subtitle. This
 * mapping returns a tight per-platform label instead.
 *
 * Lookups that miss return null; the row simply renders without a subtitle.
 * That's the right behaviour for new entries appearing via Refresh until
 * the mapping catches up.
 */
object ConsoleMapping {
    val byPackageId: Map<String, String> = mapOf(
        // PS3
        "aenu.aps3e" to "PS3",
        "net.rpcsx" to "PS3",
        // PS2
        "come.nanodata.armsx2" to "PS2",
        "xyz.aethersx2.android" to "PS2",
        "xyz.aethersx2.tturnip" to "PS2",
        "com.virtualapplications.play" to "PS2",
        // PS1
        "com.github.stenzek.duckstation" to "PS1",
        // PSP / Vita
        "org.ppsspp.ppsspp" to "PSP",
        "org.vita3k.emulator" to "PS Vita",
        // 3DS
        "org.azahar_emu.azahar" to "3DS",
        "org.citra.emu" to "3DS",
        // GameCube + Wii
        "org.dolphinemu.dolphinemu" to "GC + Wii",
        "org.dolphinemu.mmjr" to "GC + Wii",
        // Wii U
        "info.cemu.cemu" to "Wii U",
        // Switch
        "dev.eden.eden_emulator" to "Switch",
        "dev.eden.eden_nightly" to "Switch",
        // DS
        "me.magnum.melonds" to "DS",
        "me.magnum.melonds.nightly" to "DS",
        "me.magnum.melondualds" to "DS",
        // GBA
        "com.rfandango.haku_x" to "GBA",
        // Dreamcast / Naomi
        "com.flycast.emulator" to "Dreamcast + Naomi",
        // Pico-8
        "io.wip.pico8" to "Pico-8",
        // Original Xbox
        "com.izzy2lost.x1box" to "Xbox",
        // Multi-system
        "487343354" to "Many systems",
        "487343355" to "Many systems",
        "org.scummvm.scummvm" to "SCUMM",
        // PC / Windows
        "com.winlator" to "Windows",
        "com.winlator.cmod" to "Windows",
        "com.winlator.ludashi" to "Windows",
        "gamehub.lite" to "Windows",
        "app.gamenative" to "Windows"
    )
}
