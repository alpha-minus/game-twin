package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.model.GameRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ArtChain] and [BackdropLogic.gameAtSweep] are the two pieces of [JourneyBackdrop] that carry
 * real branching logic (which candidate URL is showing, which game the sweep line sits over), so
 * they are extracted as plain Kotlin and tested directly rather than through Compose/Coil, which
 * would need a fake ImageLoader interceptor for what is otherwise a two-line state machine.
 */
class JourneyBackdropTest {

    // ---- ArtChain ----

    @Test fun `current starts at the first candidate`() {
        val chain = ArtChain(listOf("a", "b", "c"))
        assertEquals("a", chain.current())
    }

    @Test fun `advance moves to the next candidate and reports true while candidates remain`() {
        val chain = ArtChain(listOf("a", "b", "c"))

        assertTrue(chain.advance())
        assertEquals("b", chain.current())

        assertTrue(chain.advance())
        assertEquals("c", chain.current())
    }

    @Test fun `advance past the last candidate reports false and current becomes null`() {
        val chain = ArtChain(listOf("a", "b"))
        chain.advance() // -> "b"

        assertFalse(chain.advance())
        assertNull(chain.current())
    }

    @Test fun `advance stays exhausted once past the end`() {
        val chain = ArtChain(listOf("a"))

        assertFalse(chain.advance())
        assertNull(chain.current())
        // A second advance on an already-exhausted chain must not throw or un-exhaust it.
        assertFalse(chain.advance())
        assertNull(chain.current())
    }

    @Test fun `empty url list starts exhausted`() {
        val chain = ArtChain(emptyList())
        assertNull(chain.current())
        assertFalse(chain.advance())
        assertNull(chain.current())
    }

    // ---- remainingDwellMillis ----

    /**
     * The stage's minimum dwell: a just-settled target still waits until the image that is up
     * has had its two seconds, however fast the sweep crosses games behind it (owner request -
     * the 150ms settle alone let a moderate pace strobe the backdrop).
     */
    @Test fun `a fresh stage swaps immediately and a busy one waits out the dwell`() {
        val ms = 1_000_000L // nanos per milli
        // Nothing shown yet: no wait.
        assertEquals(0L, remainingDwellMillis(nowNanos = 5_000 * ms, shownSinceNanos = -1L, minDwellMillis = 2_000))
        // Shown 500ms ago: 1.5s of dwell left.
        assertEquals(1_500L, remainingDwellMillis(nowNanos = 5_000 * ms, shownSinceNanos = 4_500 * ms, minDwellMillis = 2_000))
        // Shown 2s+ ago: free to swap.
        assertEquals(0L, remainingDwellMillis(nowNanos = 5_000 * ms, shownSinceNanos = 3_000 * ms, minDwellMillis = 2_000))
        assertEquals(0L, remainingDwellMillis(nowNanos = 9_000 * ms, shownSinceNanos = 3_000 * ms, minDwellMillis = 2_000))
    }

    // ---- BackdropLogic.DwellGate ----

    /**
     * The export's version of the stage's minimum dwell (owner request: the throttle the live
     * stage got did not carry into the exported file, so a video of a brisk span still strobed).
     * The gate is clocked on video time - the frame index over the export's frame rate - because
     * that is the time the person watching the file experiences.
     */
    @Test fun `the first art adopts immediately, even mid-timeline`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        assertEquals(7, gate.shownAt(timeMillis = 5_000, targetId = 7))
    }

    @Test fun `a change before the dwell has passed keeps the shown game`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        gate.shownAt(timeMillis = 0, targetId = 1)
        assertEquals(1, gate.shownAt(timeMillis = 500, targetId = 2))
        assertEquals(1, gate.shownAt(timeMillis = 1_999, targetId = 3))
    }

    @Test fun `once the dwell has passed the gate adopts whatever is current, not what it missed`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        gate.shownAt(timeMillis = 0, targetId = 1)
        gate.shownAt(timeMillis = 800, targetId = 2)   // held
        assertEquals(3, gate.shownAt(timeMillis = 2_000, targetId = 3))
    }

    @Test fun `adoption restarts the dwell`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        gate.shownAt(timeMillis = 0, targetId = 1)
        gate.shownAt(timeMillis = 2_000, targetId = 2)
        assertEquals(2, gate.shownAt(timeMillis = 3_999, targetId = 3))
        assertEquals(3, gate.shownAt(timeMillis = 4_000, targetId = 3))
    }

    @Test fun `flicker back to the shown game costs nothing - the stamp is not reset`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        gate.shownAt(timeMillis = 0, targetId = 1)
        gate.shownAt(timeMillis = 500, targetId = 2)   // held
        gate.shownAt(timeMillis = 900, targetId = 1)   // back to what is up anyway
        // The dwell still counts from t=0, so t=2000 may swap.
        assertEquals(2, gate.shownAt(timeMillis = 2_000, targetId = 2))
    }

    /**
     * Asking twice at the same instant answers the same - the invariant the export leans on: the
     * art layer consults the gate first each frame and the strip's now-playing plate consults it
     * again (owner request: the header names the game whose backdrop is up, not the sweep's), so
     * a second ask must never advance the gate a frame on its own.
     */
    @Test fun `a second ask at the same instant answers the same and moves nothing`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        gate.shownAt(timeMillis = 0, targetId = 1)

        // Held: both asks say 1, and the second must not restart or consume the dwell.
        assertEquals(1, gate.shownAt(timeMillis = 500, targetId = 2))
        assertEquals(1, gate.shownAt(timeMillis = 500, targetId = 2))
        // Adopting: the first ask swaps, the second agrees without re-stamping the dwell.
        assertEquals(2, gate.shownAt(timeMillis = 2_000, targetId = 2))
        assertEquals(2, gate.shownAt(timeMillis = 2_000, targetId = 2))
        // If the second ask above had re-stamped, this swap would still be a second away.
        assertEquals(3, gate.shownAt(timeMillis = 4_000, targetId = 3))
    }

    /** An empty stage has nothing to hold: the pre-first-unlock null adopts the first game at once. */
    @Test fun `a gate that starts before the first unlock still shows the first game immediately`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        assertNull(gate.shownAt(timeMillis = 0, targetId = null))
        assertEquals(1, gate.shownAt(timeMillis = 100, targetId = 1))
        // And that adoption armed the dwell like any other.
        assertEquals(1, gate.shownAt(timeMillis = 1_500, targetId = 2))
    }

    // ---- BackdropLogic.heaviestGameBetween ----

    /**
     * What a dwell that held through a burst adopts (owner request): not whichever game happened
     * to be last under the sweep, but the game that EARNED the window - most points across the
     * stretch the gate held through. Track and points are index-parallel by construction
     * (LayoutEngine.buildCurve appends both per unlock).
     */
    @Test fun `the heaviest game of a window wins, not the latest`() {
        // Game 1 earns 5, game 2 earns 50 across two unlocks, game 3 earns 10 - and 3 is latest.
        val track = listOf(10f to 1, 20f to 2, 30f to 2, 40f to 3)
        val points = listOf(5, 30, 20, 10)

        assertEquals(2, BackdropLogic.heaviestGameBetween(track, points, fromX = 0f, toX = 40f))
    }

    @Test fun `only the window counts, exclusive at the start`() {
        val track = listOf(10f to 1, 20f to 2, 30f to 3)
        val points = listOf(100, 5, 10)

        // The unlock AT fromX belongs to the previous window - it was already shown for.
        assertEquals(3, BackdropLogic.heaviestGameBetween(track, points, fromX = 10f, toX = 30f))
    }

    @Test fun `a tie goes to the later game, like the unweighted adopt`() {
        val track = listOf(10f to 1, 20f to 2)
        val points = listOf(10, 10)

        assertEquals(2, BackdropLogic.heaviestGameBetween(track, points, fromX = 0f, toX = 25f))
    }

    @Test fun `an empty window has no answer`() {
        val track = listOf(10f to 1)
        val points = listOf(10)

        assertNull(BackdropLogic.heaviestGameBetween(track, points, fromX = 15f, toX = 12f))
    }

    /** The gate's weighted overload: on expiry it adopts the window's pick, not the raw target. */
    @Test fun `a weighted gate adopts the heaviest game the hold crossed`() {
        val gate = BackdropLogic.DwellGate(minDwellMillis = 2_000)
        val track = listOf(10f to 1, 20f to 2, 30f to 2, 40f to 3)
        val points = listOf(5, 30, 20, 10)
        fun ask(time: Long, target: Int?, sweep: Float) =
            gate.shownAt(time, target, sweep) { a, b ->
                BackdropLogic.heaviestGameBetween(track, points, a, b)
            }

        // Fresh stage: the first target lands directly, stamping the window's start.
        assertEquals(1, ask(time = 0, target = 1, sweep = 10f))
        // Held through the burst...
        assertEquals(1, ask(time = 1_000, target = 2, sweep = 25f))
        // ...and on expiry the window (10, 40] belongs to game 2's 50 points, not to game 3.
        assertEquals(2, ask(time = 2_000, target = 3, sweep = 40f))
    }

    // ---- BackdropLogic.artCandidatesFor ----

    private fun game(
        title: String? = "/Images/title.png",
        boxArt: String? = "/Images/box.png",
        icon: String? = "/Images/icon.png",
        ingame: String? = "/Images/ingame.png",
    ) = GameRef(
        id = 1, title = "G", iconPath = icon,
        artPaths = listOfNotNull(ingame, title, boxArt, icon),
        colorIndex = 0, players = 100, award = null, titlePath = title, boxArtPath = boxArt,
        ingamePath = ingame,
    )

    @Test fun `each named source leads with its own field and falls back through the rest`() {
        val g = game()
        assertEquals(
            listOf("/Images/title.png", "/Images/box.png", "/Images/icon.png"),
            BackdropLogic.artCandidatesFor(g, ExportBackground.TITLE),
        )
        assertEquals(
            listOf("/Images/ingame.png", "/Images/title.png", "/Images/box.png", "/Images/icon.png"),
            BackdropLogic.artCandidatesFor(g, ExportBackground.INGAME),
        )
        assertEquals(
            listOf("/Images/box.png", "/Images/title.png", "/Images/icon.png"),
            BackdropLogic.artCandidatesFor(g, ExportBackground.BOX_ART),
        )
    }

    @Test fun `a game without the picked art falls back to what it has`() {
        assertEquals(
            listOf("/Images/box.png", "/Images/icon.png"),
            BackdropLogic.artCandidatesFor(game(title = null), ExportBackground.TITLE),
        )
    }

    @Test fun `NONE is the off switch, not a preference - no fallback at all`() {
        assertTrue(BackdropLogic.artCandidatesFor(game(), ExportBackground.NONE).isEmpty())
    }

    @Test fun `a repeated path is only offered once`() {
        val same = game(title = "/Images/one.png", boxArt = "/Images/one.png")
        assertEquals(
            listOf("/Images/one.png", "/Images/icon.png"),
            BackdropLogic.artCandidatesFor(same, ExportBackground.TITLE),
        )
    }

    /**
     * The icon is not a backdrop candidate in its own right any more: it is the *fallback* tier,
     * pulled out of the crop chain by the screen and drawn small and centred instead (a 64px
     * thumbnail cropped to a whole stage is a smear, not a picture of the game). What this pins
     * is that it is still offered at all - a game whose only art is its icon must not come back
     * with nothing for the screen to fall back to.
     */
    @Test fun `the icon remains available as the last art a game has`() {
        val iconOnly = game(title = null, boxArt = null, ingame = null)
        assertEquals(
            listOf("/Images/icon.png"),
            BackdropLogic.artCandidatesFor(iconOnly, ExportBackground.TITLE),
        )
    }

    // ---- BackdropLogic.gameAtSweep ----

    @Test fun `gameAtSweep returns the last unlock at or before the sweep, within the tolerance`() {
        val track = listOf(0f to 1, 10f to 2, 20f to 3)

        assertEquals(2, BackdropLogic.gameAtSweep(track, 15f))
        // 0.5f tolerance: 20 <= 19.4 + 0.5 is false, so still the previous game.
        assertEquals(2, BackdropLogic.gameAtSweep(track, 19.4f))
        // 20 <= 19.5 + 0.5 is true.
        assertEquals(3, BackdropLogic.gameAtSweep(track, 19.5f))
    }

    /**
     * The lookup is a binary search now, not a backwards scan: the track is ascending in x and can
     * carry one entry per unlock, so `lastOrNull { }` walked nearly the whole list at the low
     * progress every playback starts at. This pins it against the scan it replaced, ties and
     * repeated x values included - a run of unlocks earned at the same moment shares an x, and
     * "last one at or before the sweep" has to mean the last of the run either way.
     */
    @Test fun `gameAtSweep agrees with a backwards scan, ties included`() {
        val track = buildList {
            var x = 0f
            var game = 1
            repeat(60) { i ->
                repeat(if (i % 5 == 0) 3 else 1) { add(x to game) }
                x += 7f
                if (i % 4 == 3) game++
            }
        }
        val scan = { sweep: Float -> track.lastOrNull { (x, _) -> x <= sweep + 0.5f }?.second }

        for (tenths in -20..4400) {
            val sweep = tenths / 10f
            assertEquals("at $sweep", scan(sweep), BackdropLogic.gameAtSweep(track, sweep))
        }
    }

    @Test fun `gameAtSweep before the first unlock is null`() {
        val track = listOf(0f to 1, 10f to 2)
        assertNull(BackdropLogic.gameAtSweep(track, -1f))
    }

    @Test fun `gameAtSweep past the last unlock returns the last game`() {
        val track = listOf(0f to 1, 10f to 2, 20f to 3)
        assertEquals(3, BackdropLogic.gameAtSweep(track, 999f))
    }

    @Test fun `gameAtSweep on an empty track is null`() {
        assertNull(BackdropLogic.gameAtSweep(emptyList(), 5f))
    }

    // ---- BackdropSwapState ----

    @Test fun `initial state has nothing on either layer`() {
        val state = BackdropSwapState()
        assertNull(state.backUrl)
        assertNull(state.frontUrl)
        assertFalse(state.frontVisible)
    }

    @Test fun `first target loads onto the front layer invisibly, nothing held on back`() {
        val state = BackdropSwapState().targetChanged("a")

        assertEquals("a", state.frontUrl)
        assertFalse(state.frontVisible)
        assertNull(state.backUrl)
    }

    @Test fun `swap-before-load holds the old image at full alpha`() {
        // Settled on "a" (back), then the sweep moves onto a new game targeting "b".
        val settled = BackdropSwapState(backUrl = "a", backVisible = true)
        val swapping = settled.targetChanged("b")

        // "b" hasn't loaded yet: it must not be visible, and "a" must still be shown at full alpha.
        assertEquals("b", swapping.frontUrl)
        assertFalse(swapping.frontVisible)
        assertEquals("a", swapping.backUrl)
        assertTrue(swapping.backVisible)
    }

    @Test fun `success starts the dissolve - incoming in, held image out`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")

        val loaded = swapping.incomingLoaded("b")

        assertTrue(loaded.frontVisible)
        // "a" is still the back layer's content — it has to be there to dissolve out of — but it
        // stops being held at full alpha the moment the incoming image is confirmed on screen.
        // Holding both at full alpha until settle() is what stacked two games on the stage.
        assertEquals("a", loaded.backUrl)
        assertFalse(loaded.backVisible)
    }

    @Test fun `a stale success for a candidate that's no longer targeted is ignored`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")

        // "b" is retargeted away before it reports success; a late callback for "b" must not
        // resurrect it onto the front layer.
        val retargeted = swapping.targetChanged("c")
        val staleCallback = retargeted.incomingLoaded("b")

        assertEquals(retargeted, staleCallback)
        assertEquals("c", staleCallback.frontUrl)
        assertFalse(staleCallback.frontVisible)
    }

    @Test fun `retarget mid-load discards the unloaded candidate and keeps holding the old image`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")

        // "b" never reported back before the sweep moved on again, to "c".
        val retargeted = swapping.targetChanged("c")

        assertEquals("c", retargeted.frontUrl)
        assertFalse(retargeted.frontVisible)
        // "a" — the last thing actually shown — is still held, not "b".
        assertEquals("a", retargeted.backUrl)
        assertTrue(retargeted.backVisible)
    }

    @Test fun `settle promotes a fully faded-in front layer onto the back layer`() {
        val loaded = BackdropSwapState(backUrl = "a", backVisible = true)
            .targetChanged("b")
            .incomingLoaded("b")

        val settled = loaded.settle()

        assertEquals("b", settled.backUrl)
        assertTrue(settled.backVisible)
        assertNull(settled.frontUrl)
        assertFalse(settled.frontVisible)
    }

    @Test fun `settle is a no-op while the front layer is still loading`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")

        assertEquals(swapping, swapping.settle())
    }

    @Test fun `settle is a no-op once already settled`() {
        val settled = BackdropSwapState(backUrl = "a", backVisible = true)

        assertEquals(settled, settled.settle())
    }

    @Test fun `retargeting to the already-settled url is a no-op`() {
        val settled = BackdropSwapState(backUrl = "a", backVisible = true)

        assertEquals(settled, settled.targetChanged("a"))
    }

    @Test fun `a failed candidate clears the front layer without touching the held back image`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")

        val failed = swapping.incomingFailed("b")

        assertNull(failed.frontUrl)
        assertFalse(failed.frontVisible)
        assertEquals("a", failed.backUrl)
        assertTrue(failed.backVisible)
    }

    @Test fun `a stale failure for a candidate that's no longer targeted is ignored`() {
        val swapping = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged("b")
        val retargeted = swapping.targetChanged("c")

        val staleCallback = retargeted.incomingFailed("b")

        assertEquals(retargeted, staleCallback)
    }

    @Test fun `total failure fades the held image out gracefully instead of holding it forever`() {
        // Every candidate for the new game failed, so ArtChain is exhausted and the caller
        // reports the target as null.
        val exhausted = BackdropSwapState(backUrl = "a", backVisible = true)
            .targetChanged("b")
            .incomingFailed("b")
            .targetChanged(null)

        assertNull(exhausted.frontUrl)
        assertFalse(exhausted.frontVisible)
        // "a" is still there to animate down from, but no longer held at full alpha.
        assertEquals("a", exhausted.backUrl)
        assertFalse(exhausted.backVisible)
    }

    @Test fun `settle drops the back layer once its fade-out has finished`() {
        val fadingOut = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged(null)

        val settled = fadingOut.settle()

        assertNull(settled.backUrl)
        assertFalse(settled.backVisible)
    }

    @Test fun `coming back to the fading-out image re-holds it instead of letting it vanish`() {
        // The sweep left this game and came back to it inside one crossfade - a game the runner
        // re-enters, or a scrub. Treating "same url" as nothing to do would let the fade-out that
        // is already running finish, blanking the stage while it shows exactly the right image.
        val fadingOut = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged(null)
        assertFalse(fadingOut.backVisible)

        val revived = fadingOut.targetChanged("a")

        assertEquals("a", revived.backUrl)
        assertTrue(revived.backVisible)
        assertNull(revived.frontUrl)
    }

    @Test fun `retargeting during a fade-out cancels it and re-holds the old image`() {
        val fadingOut = BackdropSwapState(backUrl = "a", backVisible = true).targetChanged(null)

        val revived = fadingOut.targetChanged("c")

        assertEquals("c", revived.frontUrl)
        assertFalse(revived.frontVisible)
        assertEquals("a", revived.backUrl)
        assertTrue(revived.backVisible)
    }

    /**
     * The reported bug, as a state machine: "multiple background images are displayed at the same
     * time". A sweep crossing several games faster than the 800ms crossfade never gives [settle] a
     * chance to run, so every transition has to leave at most one image on the stage by itself.
     */
    @Test fun `a sweep faster than the crossfade never stacks two loaded images`() {
        var state = BackdropSwapState(backUrl = "a", backVisible = true)

        listOf("b", "c", "d").forEach { next ->
            state = state.targetChanged(next).incomingLoaded(next)
            // Mid-dissolve exactly one layer is held at full alpha: the incoming one. The outgoing
            // image is on its way out rather than parked underneath it.
            assertTrue(state.frontVisible)
            assertFalse(state.backVisible)
            assertEquals(next, state.frontUrl)
        }

        // And what is left when the sweep finally rests on one game is a single image, alone.
        val settled = state.settle()
        assertEquals("d", settled.backUrl)
        assertTrue(settled.backVisible)
        assertNull(settled.frontUrl)
        assertFalse(settled.frontVisible)
    }

    @Test fun `retargeting away from a visible front commits it rather than dropping it`() {
        // "b" has loaded and is fading in over "a" when the sweep moves on to "c". What the user is
        // actually looking at is "b", so that is what "c" must fade in over - keeping "a" would put
        // a game the sweep left two moves ago back under the incoming one.
        val fadingIn = BackdropSwapState(backUrl = "a", backVisible = true)
            .targetChanged("b")
            .incomingLoaded("b")

        val retargeted = fadingIn.targetChanged("c")

        assertEquals("b", retargeted.backUrl)
        assertTrue(retargeted.backVisible)
        assertEquals("c", retargeted.frontUrl)
        assertFalse(retargeted.frontVisible)
    }

    @Test fun `exhausting the candidates fades out the image that is actually showing`() {
        // Same shape, but the new game has no usable art at all: the visible "b" is committed and
        // then faded out, rather than "a" being the thing that fades.
        val exhausted = BackdropSwapState(backUrl = "a", backVisible = true)
            .targetChanged("b")
            .incomingLoaded("b")
            .targetChanged(null)

        assertEquals("b", exhausted.backUrl)
        assertFalse(exhausted.backVisible)
        assertNull(exhausted.frontUrl)
        assertFalse(exhausted.frontVisible)
        assertEquals(BackdropSwapState(), exhausted.settle())
    }

    @Test fun `a chain with no candidates at all never has anything to hold or fade`() {
        // The very first game's every candidate fails before anything is ever shown.
        val neverShown = BackdropSwapState()
            .targetChanged("a")
            .incomingFailed("a")
            .targetChanged(null)

        assertNull(neverShown.backUrl)
        assertFalse(neverShown.backVisible)
        assertNull(neverShown.frontUrl)
        assertEquals(BackdropSwapState(), neverShown.settle())
    }
}
