package com.twinlore.app.journey.export

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The deterministic end-of-run cleanup behind repeated exports: the run cache's frame-sized
 * native buffers are recycled when the run ends rather than left for a GC that feels no pressure
 * from their tiny Java shells, and the share cache sweeps its stale files instead of growing a
 * full MP4 per share until the system feels like reclaiming cache. Both are the "couple of
 * exports, then the app dies on the next one" report: dead native memory and dead disk stacking
 * up between runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportRunReleaseTest {

    @Test
    fun `release recycles the scaled art buffers`() {
        val cache = ExportBackdrop.ScaledArt(64, 36)
        val scaled = cache.forGame(1, ImageBitmap(16, 9))
        assertNotNull(scaled)

        cache.release()

        assertTrue(
            "the slot's native buffer must be freed at release, not at some future GC",
            scaled!!.asAndroidBitmap().isRecycled,
        )
    }

    @Test
    fun `a released cache serves fresh buffers again`() {
        val cache = ExportBackdrop.ScaledArt(64, 36)
        cache.forGame(1, ImageBitmap(16, 9))
        cache.release()

        val again = cache.forGame(2, ImageBitmap(16, 9))

        assertNotNull(again)
        assertFalse(again!!.asAndroidBitmap().isRecycled)
    }

    @Test
    fun `run cache release empties the art slots`() {
        val cache = ExportFrame.RunCache()
        val scaled = cache.art.forGame(3, ImageBitmap(16, 9))
        assertNotNull(scaled)

        cache.release()

        assertTrue(scaled!!.asAndroidBitmap().isRecycled)
    }

    @Test
    fun `stale share files are swept when a new one is created`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val stale = File(dir, "journey-old.mp4").apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - JourneyShareFiles.STALE_MILLIS - 60_000L)
        }
        val fresh = File(dir, "journey-fresh.png").apply { writeText("y") }

        JourneyShareFiles.file(context, "player", "mp4")

        assertFalse("a share older than the window is dead disk", stale.exists())
        assertTrue("a young share may still be read by the receiving app", fresh.exists())
    }

    @Test
    fun `the sweep window is measured from last modification`() {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val edge = File(dir, "journey-edge.mp4").apply {
            writeText("z")
            setLastModified(now - JourneyShareFiles.STALE_MILLIS + 60_000L)
        }

        JourneyShareFiles.sweepStale(dir, nowMillis = now)

        assertTrue("inside the window survives", edge.exists())
        assertEquals(true, edge.delete())
    }
}
