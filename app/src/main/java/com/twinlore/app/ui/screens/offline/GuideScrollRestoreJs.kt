package com.twinlore.app.ui.screens.offline

/**
 * Restarts the landing-scroll settle loop of [buildSettlingScrollJs].
 *
 * Evaluate this at the END of the host's own post-load injections — text size,
 * palette, marker highlights — so the landing is recomputed against the height
 * they left behind. A no-op on a page with no restore spliced in, on one whose
 * loop is still running, and on one the reader has already taken over from.
 *
 * Single-use: the hook is cleared as it is called. WebView fires onPageFinished
 * again for a same-document hash navigation, and re-arming there would drag a
 * reader who had just tapped an in-guide anchor back to where they had left off.
 */
const val SETTLE_SCROLL_REARM_JS: String =
    "(function(){var f=window.gtSettleScroll;" +
        "if(f){window.gtSettleScroll = null;f()}})();"

/**
 * Whether a scroll ratio just read off a guide's WebView describes where the
 * READER is, and may therefore be persisted as their place in that guide.
 *
 * The interesting case is 0.0. The top of a guide is a real reading position:
 * scroll back up, leave, and you expect to come back to the top. Both viewers
 * used to refuse to save a zero, so that reader was returned to wherever they
 * had been *before* scrolling up, sometimes most of a guide away.
 *
 * The refusal was not arbitrary. Between a load and the end of the landing, the
 * offset belongs to the restore's progress rather than to the reader: it starts
 * at 0 against a blank document and passes through whatever the reflows leave
 * behind. Persisting that overwrites the very position being restored, and
 * "never save a zero" was a cheap way to block the worst of it — at the cost of
 * this bug, and while still letting a mid-restore NON-zero offset through.
 *
 * [landingPending] is the honest version of the same guard: the settle loop in
 * [buildSettlingScrollJs] says when it is moving the page and when it has
 * stopped, so a read is refused for exactly as long as it is not the reader's.
 * A guide with no saved position splices no loop, nothing ever reports itself
 * pending, and its first read saves normally.
 */
internal fun mayPersistScrollRatio(ratio: Double, landingPending: Boolean): Boolean =
    ratio.isFinite() && !landingPending

/**
 * [buildSettlingScrollJs]'s `landingCall` for the guide viewers, both of which
 * expose their bridge as `androidInterface`. Guarded on the method rather than
 * just the object: the same bridge is injected on pages built before this
 * existed.
 */
internal const val ANDROID_INTERFACE_LANDING_CALL: String =
    "if (window.androidInterface && window.androidInterface.onScrollLandingChanged) " +
        "window.androidInterface.onScrollLandingChanged(active);"

/** Poll cadence of the settle loop, in milliseconds. */
internal const val SCROLL_SETTLE_TICK_MS = 100

/** Ticks the height must hold still before the landing is considered final. */
internal const val SCROLL_SETTLE_STABLE_TICKS = 5

/** Hard ceiling on the number of ticks, so a page that never settles is dropped. */
internal const val SCROLL_SETTLE_MAX_TICKS = 40

/**
 * Wraps a landing scroll in a loop that keeps re-applying it until the document
 * stops changing height.
 *
 * A guide's reading position is persisted as a ratio of the document height, so
 * the target is only as good as the height it gets multiplied by — and the
 * height at DOMContentLoaded is not the height the reader left. The app moves it
 * afterwards, every time:
 *
 *  - `onPageFinished` injects the text-size slider as an explicit body
 *    font-size ([buildTextZoomJs]). At 100% that is 16px against the text
 *    autosizer's measured 18.9678px, so the document loses about a sixth of its
 *    height a beat after it loaded.
 *  - That same call runs `gtApplyFormat()`, which re-fits every ASCII block with
 *    a transform and a compensating negative margin.
 *  - The palette pass rewrites font-family, which changes line metrics.
 *  - A ResizeObserver refits 100ms behind any of the above.
 *  - On saved web guides, images and web fonts land in the same window.
 *
 * Applying the ratio once against the pre-shrink height leaves the reader
 * `ratio * (heightThen - heightNow)` too far down: nothing at the top of a
 * guide, half a chapter past the middle. This has been reported twice. The first
 * fix added a re-apply at window.load, which the font-size work then outran.
 *
 * So rather than chase each mutation, this re-runs [body] on every height change
 * until the height has held for [SCROLL_SETTLE_STABLE_TICKS] ticks, gives up
 * after [SCROLL_SETTLE_MAX_TICKS], and starts a fresh pass at window.load for
 * anything that lands after the first one settled.
 *
 * [body] must recompute its target from the live layout — a ratio against the
 * current scrollHeight, an element's current rect — never from a value measured
 * when this string was built. That is the whole point of running it repeatedly.
 *
 * [landingCall] is a JS statement run with a boolean `active` in scope whenever
 * a pass starts and whenever one ends, however it ends. Hosts use it to tell
 * when the offset on screen is the restore's doing rather than the reader's;
 * see [mayPersistScrollRatio]. Empty by default, since not every host listens.
 *
 * A drag, a wheel or a key ends the loop on the spot: once the reader is
 * scrolling for themselves, a re-apply is the app pulling the page away from
 * them. A plain tap is not one of those — tapping is how this app's chrome is
 * revealed and it moves nothing, so ending the pass on `touchstart` would
 * strand a guide that was opened and tapped in the same second. Neither is
 * `scroll`, which the loop's own `window.scrollTo` raises.
 *
 * Scrolls that arrive from neither the reader nor an event — a chapter tap
 * routed through `scrollIntoView`, a find result, a marker jump — are caught by
 * comparing the offset against the one the last apply produced. That check runs
 * only while the height is still: an offset that moves *with* the height is the
 * very thing this loop exists to correct, and must not be mistaken for someone
 * else driving.
 *
 * The loop is also published as `window.gtSettleScroll` so the page's host can
 * re-arm it after mutating the document itself ([SETTLE_SCROLL_REARM_JS]). The
 * font-size injection above arrives on the UI thread, so how long after load it
 * lands depends on how busy that thread is; on a large guide it can miss the
 * settle window altogether. Re-arming from the callback that does the injecting
 * makes the correction ordered instead of raced. Re-arming a loop that is still
 * running is a no-op, and one the reader has already taken over from stays
 * stopped.
 */
internal fun buildSettlingScrollJs(body: String, landingCall: String = ""): String = """
(function() {
    var lastH = -1, stable = 0, ticks = 0, stopped = false, running = false;
    var appliedY = -1;
    function notify(active) {
        try { $landingCall } catch (e) {}
    }
    function apply() {
$body
        appliedY = Math.round(window.pageYOffset || 0);
    }
    function height() {
        return Math.max(
            document.body ? document.body.scrollHeight : 0,
            document.documentElement ? document.documentElement.scrollHeight : 0);
    }
    function stop() { stopped = true; }
    ['touchmove', 'wheel', 'keydown'].forEach(function(type) {
        window.addEventListener(type, stop, { passive: true, capture: true, once: true });
    });
    function finish() { running = false; notify(false); }
    function tick() {
        if (stopped) { finish(); return; }
        var h = height();
        if (h !== lastH) {
            lastH = h;
            stable = 0;
            try { apply(); } catch (e) {}
        } else {
            // Still height, moved page: someone else is driving. See the KDoc.
            if (appliedY >= 0 &&
                Math.abs(Math.round(window.pageYOffset || 0) - appliedY) > 4) {
                stopped = true;
                finish();
                return;
            }
            stable++;
        }
        if (stable >= $SCROLL_SETTLE_STABLE_TICKS || ++ticks >= $SCROLL_SETTLE_MAX_TICKS) {
            finish();
            return;
        }
        setTimeout(tick, $SCROLL_SETTLE_TICK_MS);
    }
    function loop() {
        if (running || stopped) return;
        running = true;
        lastH = -1; stable = 0; ticks = 0;
        notify(true);
        tick();
    }
    window.gtSettleScroll = loop;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', loop, { once: true });
    } else {
        loop();
    }
    if (document.readyState !== 'complete') {
        window.addEventListener('load', loop, { once: true });
    }
})();
""".trimIndent()
