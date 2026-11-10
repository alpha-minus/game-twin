package com.twinlore.app.data

import android.os.Build

/**
 * When to ask for All-Files access.
 *
 * The permission is a speed optimisation, not a requirement: with it granted
 * [com.twinlore.app.data.library.ResourceResolver] takes its filesystem fast path
 * instead of SAF's per-child Binder queries. Below a handful of vaults that cost is
 * invisible, so asking on first launch would be a scary system-settings trip in
 * exchange for nothing the user can perceive. Past ten vaults it is worth the ask.
 *
 * Pure so it can be tested without Robolectric or a mocked ViewModel graph: the
 * caller reads the four inputs from the real world and passes them in.
 */
object AllFilesAccessPromptDecision {

    /** Libraries larger than this are big enough for the SAF path to be felt. */
    const val VAULT_THRESHOLD = 10

    fun shouldPrompt(
        sdkInt: Int,
        granted: Boolean,
        vaultCount: Int,
        declined: Boolean,
    ): Boolean =
        sdkInt >= Build.VERSION_CODES.R &&
            !granted &&
            vaultCount > VAULT_THRESHOLD &&
            !declined
}
