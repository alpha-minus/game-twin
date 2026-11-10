package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A guide's reading position is a ratio of the document's height, so it is only
 * as good as the height it is multiplied by — and the height at DOMContentLoaded
 * is not the one the reader left. The app shrinks the document afterwards: the
 * text-size slider goes on as an explicit body font-size in onPageFinished, that
 * call re-fits every ASCII block, the palette pass swaps the font-family, and a
 * ResizeObserver refits behind all of it.
 *
 * Multiplying the ratio by the pre-shrink height leaves the reader
 * `ratio * (heightThen - heightNow)` too far down — nothing at the top of a
 * guide, half a chapter past the middle. These tests pin the settle loop that
 * makes the landing independent of when the page stops moving.
 */
class GuideScrollRestoreJsTest {

    private val body = "window.scrollTo(0, 42);"

    @Test
    fun `re-applies the landing scroll on every height change`() {
        // The single most important line: the target is recomputed against the
        // live layout each time the document resizes, instead of being measured
        // once and trusted.
        val js = buildSettlingScrollJs(body)
        assertTrue("missing height-change re-apply", js.contains("h !== lastH"))
        assertTrue("landing body not run from the loop", js.contains(body))
    }

    @Test
    fun `settles only once the height has held still`() {
        val js = buildSettlingScrollJs(body)
        assertTrue(
            "missing stability window",
            js.contains("stable >= $SCROLL_SETTLE_STABLE_TICKS"),
        )
        assertTrue(
            "missing poll cadence",
            js.contains("setTimeout(tick, $SCROLL_SETTLE_TICK_MS)"),
        )
    }

    @Test
    fun `gives up instead of tracking the page forever`() {
        assertTrue(
            "missing tick ceiling",
            buildSettlingScrollJs(body).contains("ticks >= $SCROLL_SETTLE_MAX_TICKS"),
        )
    }

    @Test
    fun `the stability window and the ceiling leave room for a late reflow`() {
        // onPageFinished's font-size injection is the reflow that reopened this
        // bug and it lands within a second of load; the ResizeObserver refit
        // trails a resize by 100ms. Half a second of stillness clears both, and
        // the ceiling has to outlast them by a wide margin.
        assertTrue(SCROLL_SETTLE_STABLE_TICKS * SCROLL_SETTLE_TICK_MS >= 500)
        assertTrue(SCROLL_SETTLE_MAX_TICKS * SCROLL_SETTLE_TICK_MS >= 4000)
    }

    @Test
    fun `the reader scrolling for themselves ends the restore`() {
        // Once someone is driving the page themselves, a late re-apply is the
        // app yanking it back out from under them.
        val js = buildSettlingScrollJs(body)
        for (event in listOf("touchmove", "wheel", "keydown")) {
            assertTrue("no abort on $event", js.contains("'$event'"))
        }
        assertTrue("abort flag never set", js.contains("stopped = true"))
        assertTrue("abort flag never read", js.contains("if (stopped)"))
    }

    @Test
    fun `a tap alone does not end the restore`() {
        // Tapping is how the reader reveals this app's chrome, and it moves
        // nothing. Ending the pass on touchstart would strand a guide opened
        // and tapped in the same second at whatever height it had then.
        val js = buildSettlingScrollJs(body)
        assertFalse("a tap must not cancel the landing", js.contains("'touchstart'"))
        assertFalse("a tap must not cancel the landing", js.contains("'pointerdown'"))
        // 'scroll' must not abort either: the loop's own scrollTo raises it, so
        // listening would end the pass on the first thing it does.
        assertFalse("aborting on scroll would self-abort", js.contains("'scroll'"))
    }

    @Test
    fun `it yields to anything else that moves the page`() {
        // A chapter tap, a find result and a marker jump all scroll the page
        // from outside this loop, and none of them raises touchmove. If the
        // page has moved off the last landing while the height sat still, the
        // reader has been taken somewhere on purpose and the resume position is
        // no longer wanted. Checked ONLY when the height held: a height change
        // is the case this whole loop exists to re-apply through.
        val js = buildSettlingScrollJs(body)
        assertTrue("landing position never recorded", js.contains("appliedY = "))
        assertTrue("landing position never compared", js.contains("- appliedY"))
    }

    @Test
    fun `the re-arm is spent by the load it belongs to`() {
        // WebView calls onPageFinished again for a same-document hash
        // navigation. Re-arming there would drag a reader who had just tapped
        // an in-guide anchor back to where they had left off.
        assertTrue(SETTLE_SCROLL_REARM_JS.contains("gtSettleScroll = null"))
    }

    @Test
    fun `the app can re-arm the loop after its own late reflow`() {
        // The reflow that reopened this bug — the text-size font-size injected
        // from onPageFinished — arrives on the UI thread, so how long after
        // load it lands depends on how busy that thread is. On a big guide it
        // can miss the settle window entirely. Re-arming from the same callback
        // that does the injecting makes the correction ordered rather than
        // raced.
        val js = buildSettlingScrollJs(body)
        assertTrue("no re-arm hook", js.contains("window.gtSettleScroll"))
        assertTrue("re-arm hook does not call the loop", SETTLE_SCROLL_REARM_JS.contains("gtSettleScroll"))
    }

    @Test
    fun `re-arming cannot stack a second loop on top of a running one`() {
        assertTrue(buildSettlingScrollJs(body).contains("if (running || stopped) return;"))
    }

    @Test
    fun `runs at DOMContentLoaded and again at window load`() {
        // Images and web fonts on a saved web guide can land after the first
        // pass has settled; load restarts it rather than leaving the reader
        // wherever the intermediate height put them.
        val js = buildSettlingScrollJs(body)
        assertTrue(js.contains("'DOMContentLoaded'"))
        assertTrue(js.contains("'load'"))
    }

    @Test
    fun `a body that throws does not strand the loop`() {
        assertTrue(buildSettlingScrollJs(body).contains("catch"))
    }

    @Test
    fun `it announces when a landing starts and when it is over`() {
        // The host has to know when the page is still being moved into place,
        // because a position read during that window describes the restore's
        // progress rather than the reader's place in the guide.
        val js = buildSettlingScrollJs(body, landingCall = "MARK(active);")
        assertTrue("landing never announced", js.contains("MARK(active);"))
        assertTrue("start not announced", js.contains("notify(true)"))
        assertTrue("end not announced", js.contains("notify(false)"))
    }

    @Test
    fun `every way out of the loop announces the end`() {
        // Settling, hitting the ceiling, the reader taking over and something
        // else moving the page are four different exits. A host left believing
        // a landing is still running would stop saving the reading position
        // altogether.
        val js = buildSettlingScrollJs(body, landingCall = "MARK(active);")
        assertTrue(js.contains("function finish()"))
        // The reader took over, something else moved the page, the height
        // settled or the ceiling ran out: three exits, three finish() calls.
        assertTrue(
            "an exit path leaves without announcing the end",
            js.split("finish();").size - 1 == 3,
        )
    }

    @Test
    fun `a host with no bridge is simply not told`() {
        // The default has to be inert: buildSettlingScrollJs is also used where
        // nothing is listening.
        val js = buildSettlingScrollJs(body)
        assertTrue(js.contains("function notify("))
        assertFalse(js.contains("MARK"))
    }

    @Test
    fun `a reader who scrolled back to the top has that saved`() {
        // The reported miss: the top of a guide is a real reading position, and
        // refusing to persist it left the previous, deeper position in place, so
        // reopening dropped the reader back down where they used to be.
        assertTrue(mayPersistScrollRatio(0.0, landingPending = false))
    }

    @Test
    fun `a position read while the page is still being placed is not saved`() {
        // Between load and the end of the landing, the offset belongs to the
        // restore's progress, not to the reader. Persisting it overwrites the
        // very position being restored - which is what made refusing zero look
        // necessary in the first place.
        assertFalse(mayPersistScrollRatio(0.0, landingPending = true))
        assertFalse(mayPersistScrollRatio(0.42, landingPending = true))
    }

    @Test
    fun `a ratio that is not a number is never saved`() {
        assertFalse(mayPersistScrollRatio(Double.NaN, landingPending = false))
        assertFalse(mayPersistScrollRatio(Double.POSITIVE_INFINITY, landingPending = false))
    }
}
