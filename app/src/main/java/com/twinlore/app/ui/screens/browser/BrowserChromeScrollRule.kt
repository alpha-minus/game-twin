package com.twinlore.app.ui.screens.browser

/**
 * Pure scroll-delta-to-pad reducer for the Browser-tab chrome auto-hide rule.
 *
 * Extracted from BrowserScreen's `vm.scrollPosition.collect { … }` so the
 * isLoading short-circuit (chrome must stay fully reserved while a page is
 * loading) is unit-testable in isolation.
 *
 *  - `currentPad`: WebView's current top padding in px (0..maxPad).
 *  - `delta`:      scrollY change since the last sample (positive = scrolled down).
 *  - `maxPad`:     full chrome height in px.
 *  - `isLoading`:  when true, scroll deltas are ignored so a downward scroll
 *                  mid-load can't drain the padding out from under the user.
 *
 * Returns the new pad value, clamped to [0, maxPad]. The caller is responsible
 * for the at-top (scrollY == 0 → maxPad) anchor; that branch stays in
 * BrowserScreen so the at-top check has direct access to the StateFlow value.
 */
internal fun applyScrollDelta(
    currentPad: Float,
    delta: Float,
    maxPad: Float,
    isLoading: Boolean
): Float {
    if (isLoading) return currentPad
    return (currentPad - delta).coerceIn(0f, maxPad)
}
