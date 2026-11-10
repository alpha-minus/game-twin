package com.twinlore.app.domain

/**
 * One entry in the recommended-apps catalog.
 *
 * Entries are parsed from the upstream Obtainium-Emulation-Pack JSON for the
 * user's selected [DeviceType] (single-screen or dual-screen). The flags that
 * used to mark Standard vs Dual-Screen support on each row are gone — the user
 * picks their device once and the catalog only ever shows entries relevant to
 * that device.
 *
 * @property id Android package name from the upstream entry. Unique within
 *   a single device-type catalog; used as the row selection key.
 * @property name display name.
 * @property author upstream author/owner (e.g. GitHub user).
 * @property category which tab this row appears under. PC Emulation entries
 *   are folded into [Category.Emulator].
 * @property sourceUrl project home page (the upstream entry's `url` field).
 * @property about short description, console-focused for [Category.Emulator]
 *   entries (see [com.twinlore.app.data.catalog.ConsoleMapping]).
 * @property entryJson the raw upstream JSON object for this entry, serialized
 *   as a single line. Kept so per-row actions can rebuild an `obtainium://`
 *   deeplink without re-parsing or re-fetching.
 */
data class RecommendedApp(
    val id: String,
    val name: String,
    val author: String,
    val category: Category,
    val sourceUrl: String,
    val about: String,
    val entryJson: String
)

enum class Category(val displayName: String) {
    Emulator("Emulator"),
    Frontend("Frontend"),
    Streaming("Streaming"),
    TrackOnly("Track Only"),
    Utility("Utility")
}

/**
 * The kind of handheld the user has selected. Drives which upstream JSON we
 * load from. Persisted in [com.twinlore.app.data.SettingsRepository] so the
 * choice survives across launches.
 */
enum class DeviceType(val displayName: String, val assetFile: String) {
    SingleScreen("Single screen", "catalog/single-screen.json"),
    DualScreen("Dual screen", "catalog/dual-screen.json")
}
