package com.twinlore.app.ui.screens.launcher

/**
 * The starter set of pinned apps, in the order they should appear: settings, files, a browser,
 * YouTube.
 *
 * Each slot lists the package names that can fill it, most likely first, because these vary by
 * OEM and by which of Google's apps a device ships. A slot nothing satisfies is simply skipped
 * rather than leaving a hole, so a device with no Chrome gets three pins, not three and a gap.
 *
 * Matched against [installed] — the packages that actually resolve a launcher intent — so a
 * package present but unlaunchable is never pinned.
 */
fun defaultPins(installed: Set<String>): List<String> = DEFAULT_PIN_SLOTS
    .mapNotNull { candidates -> candidates.firstOrNull { it in installed } }

private val DEFAULT_PIN_SLOTS: List<List<String>> = listOf(
    listOf("com.android.settings"),
    listOf("com.google.android.documentsui", "com.android.documentsui"),
    listOf("com.android.chrome", "com.chrome.beta"),
    listOf("com.google.android.youtube", "app.revanced.android.youtube"),
)
