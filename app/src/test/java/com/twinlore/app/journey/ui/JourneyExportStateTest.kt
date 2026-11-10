package com.twinlore.app.journey.ui

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which file the export notice is about.
 *
 * The notice carries one message and one "Share again" beside it, and the screen can produce two
 * kinds of file. Whether the button hands over the right one is entirely a question of this class's
 * bookkeeping, which is why the exporters are lambdas here: a fake that returns a uri is all this
 * needs, and a MediaStore is not something a unit test has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyExportStateTest {

    private val videoUri: Uri = Uri.parse("content://media/external/video/media/1")
    private val imageUri: Uri = Uri.parse("content://media/external/images/media/2")

    private fun request(): JourneyExportRequest {
        val unlock = Unlock(
            id = 1, title = "A", description = "", points = 10, badgeName = "b",
            type = AchievementType.STANDARD, earnedAtMillis = 1_000L, numAwarded = 1, gameId = 1,
        )
        val main = PlayerJourney(
            name = "me", unlocks = listOf(unlock),
            games = mapOf(1 to GameRef(1, "G", null, emptyList(), 0, 10, null)),
            medals = emptyList(),
        )
        val frame = JourneyFrame(JourneyMode.SPAN, main, emptyList(), 0L, 2_000L)
        return JourneyExportRequest(
            layout = LayoutEngine.layout(frame, includeNodes = true),
            frame = frame,
            style = ChartStyle(
                background = Color.Black, panel = Color.DarkGray, grid = Color.Gray,
                ink = Color.White, inkDim = Color.LightGray, accent = Color.Cyan,
                seriesColors = listOf(Color.Magenta), rivalColors = listOf(Color.Green),
                typeColors = AchievementType.values().associateWith { Color.Red },
                silver = Color.LightGray, gold = Color.Yellow, labelFont = FontFamily.Default,
            ),
            bitmaps = emptyMap(),
            art = emptyMap(),
            userName = "me",
            speed = 1f,
        )
    }

    private fun stateIn(scope: TestScope) = JourneyExportState(
        exportVideo = { _, _, _ -> videoUri },
        exportImage = { _, _ -> imageUri },
        scope = scope,
    )

    /**
     * An export that does not finish until the test says so, so one kind can be held in flight
     * while the other is started. Everything else in this file completes its export before touching
     * the state again, which is exactly why the interleaving below went unnoticed.
     */
    private class Gate {
        private val released = CompletableDeferred<Unit>()
        var reachedProgress = 0 to 0
            private set

        suspend fun await() = released.await()
        fun release() = released.complete(Unit)
        fun record(done: Int, total: Int) { reachedProgress = done to total }
    }

    /**
     * The bug: starting one kind of export cleared its own saved file and left the other's behind,
     * so exporting a video and then an image produced a notice reading "Image saved" with a
     * "Share again" that handed over the video.
     */
    @Test fun `an image export forgets the video that came before it`() = runTest {
        val state = stateIn(this)

        state.start(request())
        advanceUntilIdle()
        assertEquals(videoUri, state.savedUri)

        state.startImage(request())
        advanceUntilIdle()

        assertEquals(imageUri, state.imageUri)
        assertNull("the notice would have shared the video it no longer names", state.savedUri)
        assertEquals("Image ready to share.", state.message)
    }

    @Test fun `a video export forgets the image that came before it`() = runTest {
        val state = stateIn(this)

        state.startImage(request())
        advanceUntilIdle()
        assertEquals(imageUri, state.imageUri)

        state.start(request())
        advanceUntilIdle()

        assertEquals(videoUri, state.savedUri)
        assertNull("the notice would have shared the image it no longer names", state.imageUri)
        assertEquals("Video ready to share.", state.message)
    }

    /** And a dismissal forgets both, so the next notice starts from nothing. */
    @Test fun `dismissing clears both kinds`() = runTest {
        val state = stateIn(this)
        state.start(request())
        advanceUntilIdle()

        state.dismiss()

        assertNull(state.savedUri)
        assertNull(state.imageUri)
        assertNull(state.message)
    }

    /** Each finished file offers the share sheet exactly once. */
    @Test fun `the auto-share of each kind is a one-shot`() = runTest {
        val state = stateIn(this)

        state.startImage(request())
        advanceUntilIdle()
        assertEquals(true, state.consumeImageShare())
        assertEquals(false, state.consumeImageShare())

        state.start(request())
        advanceUntilIdle()
        assertEquals(true, state.consumeAutoShare())
        assertEquals(false, state.consumeAutoShare())
        // Starting the video cleared the image's pending share along with its uri, so the picture
        // that is no longer named cannot open a chooser of its own.
        assertEquals(false, state.consumeImageShare())
    }
    // ---- one kind started while the other is in flight ----

    /**
     * The regression a shared generation token introduced.
     *
     * The two starters are two buttons with no lockout between them, so an image can be asked for
     * while a video is encoding. With one counter for both, starting the image bumped it out from
     * under the running encode: its progress writes, its completion notice and its finally-block
     * cleanup all no-opped, leaving a frame counter that never went away and a finished video whose
     * notice was silently dropped. A job may only ever be superseded by another of its own kind.
     */
    @Test fun `an image started mid-encode does not strand the video`() = runTest {
        val gate = Gate()
        val state = JourneyExportState(
            exportVideo = { _, _, onProgress ->
                gate.await()
                onProgress(30, 30)
                videoUri
            },
            exportImage = { _, _ -> imageUri },
            scope = this,
        )

        state.start(request())
        advanceUntilIdle()
        assertEquals("the video must be in flight", 1, state.progress?.first)

        state.startImage(request())
        advanceUntilIdle()
        assertEquals(imageUri, state.imageUri)

        gate.release()
        advanceUntilIdle()

        // The encode finishes normally: its notice lands, its counter is cleared, and the share
        // sheet is offered exactly once.
        assertEquals(videoUri, state.savedUri)
        assertEquals("Video ready to share.", state.message)
        assertNull("the frame counter was left on screen for good", state.progress)
        assertEquals(true, state.consumeAutoShare())
        // ... and the picture it superseded is no longer named, so nothing can share the wrong file.
        assertNull(state.imageUri)
    }

    /** The mirror image, which the same shared counter broke in the same way. */
    @Test fun `a video started mid-render does not strand the image`() = runTest {
        val gate = Gate()
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> videoUri },
            exportImage = { _, _ ->
                gate.await()
                imageUri
            },
            scope = this,
        )

        state.startImage(request())
        advanceUntilIdle()
        assertEquals("the image must be in flight", true, state.imageRunning)

        state.start(request())
        advanceUntilIdle()
        assertEquals(videoUri, state.savedUri)

        gate.release()
        advanceUntilIdle()

        assertEquals(imageUri, state.imageUri)
        assertEquals("Image ready to share.", state.message)
        assertEquals("the Saving label was left on the button for good", false, state.imageRunning)
        assertEquals(true, state.consumeImageShare())
        assertNull(state.savedUri)
    }

    /** A second press of the same button while that kind is still running is ignored, as before. */
    @Test fun `each kind refuses to start twice at once`() = runTest {
        val gate = Gate()
        var videoStarts = 0
        var imageStarts = 0
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> videoStarts++; gate.await(); videoUri },
            exportImage = { _, _ -> imageStarts++; gate.await(); imageUri },
            scope = this,
        )

        state.start(request())
        state.startImage(request())
        advanceUntilIdle()
        state.start(request())
        state.startImage(request())
        advanceUntilIdle()

        assertEquals(1, videoStarts)
        assertEquals(1, imageStarts)
        gate.release()
        advanceUntilIdle()
    }


    /**
     * UI feedback: sharing does not save any more. Share-mode files go to the sheet from the
     * app's own cache; only "Save to device" writes the gallery, says so, and does NOT open the
     * share sheet by itself.
     */
    @Test
    fun `save mode says saved and does not auto-share`() = runTest {
        val state = JourneyExportState(
            exportVideo = { _, share, _ ->
                assertEquals("save mode must not render a share-only file", false, share)
                videoUri
            },
            exportImage = { _, share ->
                assertEquals(false, share)
                imageUri
            },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        state.start(request(), share = false)
        advanceUntilIdle()
        assertEquals("Video saved to Movies/GameTwin.", state.message)
        assertEquals(false, state.consumeAutoShare())

        state.startImage(request(), share = false)
        advanceUntilIdle()
        assertEquals("Image saved to Pictures/GameTwin.", state.message)
        assertEquals(false, state.consumeImageShare())
    }

    @Test
    fun `successful video export fires the success callback once`() = runTest(UnconfinedTestDispatcher()) {
        var succeeded = 0
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> videoUri },
            exportImage = { _, _ -> imageUri },
            scope = this,
            onExportSucceeded = { succeeded++ },
        )
        state.start(request(), share = false)
        advanceUntilIdle()
        assertEquals(1, succeeded)
    }

    @Test
    fun `successful image export fires the success callback once`() = runTest(UnconfinedTestDispatcher()) {
        var succeeded = 0
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> videoUri },
            exportImage = { _, _ -> imageUri },
            scope = this,
            onExportSucceeded = { succeeded++ },
        )
        state.startImage(request(), share = true)
        advanceUntilIdle()
        assertEquals(1, succeeded)
    }

    @Test
    fun `failed export does not fire the success callback`() = runTest(UnconfinedTestDispatcher()) {
        var succeeded = 0
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> throw RuntimeException("encoder died") },
            exportImage = { _, _ -> throw RuntimeException("no disk") },
            scope = this,
            onExportSucceeded = { succeeded++ },
        )
        state.start(request(), share = false)
        advanceUntilIdle()
        state.startImage(request(), share = false)
        advanceUntilIdle()
        assertEquals(0, succeeded)
    }

    @Test
    fun `cancelled export does not fire the success callback`() = runTest(UnconfinedTestDispatcher()) {
        var succeeded = 0
        val gateDeferred = CompletableDeferred<Unit>()
        val state = JourneyExportState(
            exportVideo = { _, _, _ -> gateDeferred.await(); videoUri },
            exportImage = { _, _ -> imageUri },
            scope = this,
            onExportSucceeded = { succeeded++ },
        )
        state.start(request(), share = false)
        state.cancel()
        gateDeferred.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, succeeded)
    }
}
