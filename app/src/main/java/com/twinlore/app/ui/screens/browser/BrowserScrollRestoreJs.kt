package com.twinlore.app.ui.screens.browser

/**
 * Builds the JS that restores a browser tab's scroll position after a
 * tab-switch / session reload.
 *
 * Browser mode persists an *absolute* `scrollY` per tab (no ratio — that path is
 * disabled here). A reopened tab re-loads the page from scratch, so its height is
 * rebuilt live: images decode in, and — when privacy blocking is on — the cosmetic
 * layer removes banner / ad containers, leaving the reopened page SHORTER than it
 * was when `scrollY` was saved. A single `window.scrollTo(0, savedY)` fired at
 * `onPageFinished` then has the WebView clamp an out-of-range `savedY` to
 * max-scroll, dumping the user at the bottom of the page.
 *
 * This routine instead polls until the document height settles (re-applying on
 * each change) and resolves the target as:
 *  - honor `savedY` whenever it's reachable,
 *  - clamp to the new bottom when the page shrank only slightly (within one
 *    viewport — the user was genuinely near the bottom),
 *  - fall back to the TOP when `savedY` is unreachable by more than a viewport
 *    (page is genuinely shorter, stale, or not yet laid out) — the user expects
 *    the top there, never the bottom.
 */
internal object BrowserScrollRestoreJs {

    /** Tick cadence + bounds: 100ms ticks, settle after 500ms stable, ceiling 2s. */
    fun build(targetY: Int): String = """
        (function(){
          var target = $targetY;
          var lastH = -1, stable = 0, ticks = 0, done = false;
          function metrics(){
            var h = Math.max(
              document.body ? document.body.scrollHeight : 0,
              document.documentElement ? document.documentElement.scrollHeight : 0);
            return { h: h, max: Math.max(0, h - window.innerHeight) };
          }
          function settle(){
            var m = metrics();
            // Unreachable by more than a viewport => page is genuinely shorter
            // (or stale / not laid out): land at the top, not the bottom.
            if (target - m.max > window.innerHeight) window.scrollTo(0, 0);
            else window.scrollTo(0, Math.min(target, m.max));
          }
          function tick(){
            if (done) return;
            var m = metrics();
            if (m.h !== lastH) {
              // While the page grows, ride min(target, max) so we track the
              // bottom upward toward the target instead of clamping early.
              lastH = m.h; stable = 0;
              window.scrollTo(0, Math.min(target, m.max));
            } else {
              stable++;
            }
            if (stable >= 5 || ++ticks >= 20) { done = true; settle(); return; }
            setTimeout(tick, 100);
          }
          tick();
        })();
    """.trimIndent()
}
