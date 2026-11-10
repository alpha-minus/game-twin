package com.twinlore.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkThumbnailCacheTest {

    private lateinit var context: Context
    private lateinit var cache: ArtworkThumbnailCache
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cache = ArtworkThumbnailCache(context)
        sourceFile = File.createTempFile("art-source", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        sourceFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @After
    fun tearDown() {
        sourceFile.delete()
        File(context.filesDir, ArtworkThumbnailCache.CACHE_DIR_NAME).deleteRecursively()
        File(context.filesDir, "carousel-thumbs").deleteRecursively()
    }

    @Test
    fun `pathFor returns null when no thumb exists`() {
        assertNull(cache.pathFor("vault-42"))
    }

    @Test
    fun `generate produces a thumb file with width 768 or less`() = runTest {
        val result = cache.generate("vault-42", Uri.fromFile(sourceFile))

        assertNotNull("generate should return the thumb File on success", result)
        assertTrue("thumb file should exist", result!!.exists())
        assertTrue("thumb file should be non-empty", result.length() > 0)

        val decoded = android.graphics.BitmapFactory.decodeFile(result.absolutePath)
        assertNotNull("thumb should decode to a valid bitmap", decoded)
        // assertEquals, not <=: the fixture source is 2048 wide, so a correct
        // downsample lands on exactly TARGET_WIDTH_PX. A `<=` assertion would pass
        // just as happily at the old 480, which is the one thing this test exists
        // to catch.
        assertEquals(
            "thumb should be downsampled to exactly TARGET_WIDTH_PX",
            ArtworkThumbnailCache.TARGET_WIDTH_PX,
            decoded.width
        )
        decoded.recycle()
    }

    @Test
    fun `invalidate deletes an existing thumb`() = runTest {
        cache.generate("vault-42", Uri.fromFile(sourceFile))
        assertNotNull(cache.pathFor("vault-42"))

        cache.invalidate("vault-42")

        assertNull(cache.pathFor("vault-42"))
    }

    @Test
    fun `invalidate is a no-op when no thumb exists`() = runTest {
        cache.invalidate("vault-never-existed")
        assertNull(cache.pathFor("vault-never-existed"))
    }

    @Test
    fun `wipeAll removes every thumb`() = runTest {
        cache.generate("vault-1", Uri.fromFile(sourceFile))
        cache.generate("vault-2", Uri.fromFile(sourceFile))
        cache.generate("vault-3", Uri.fromFile(sourceFile))

        cache.wipeAll()

        assertNull(cache.pathFor("vault-1"))
        assertNull(cache.pathFor("vault-2"))
        assertNull(cache.pathFor("vault-3"))
    }

    @Test
    fun `generate leaves no tmp file behind on success`() = runTest {
        cache.generate("vault-42", Uri.fromFile(sourceFile))
        val cacheDirFile = File(context.filesDir, ArtworkThumbnailCache.CACHE_DIR_NAME)
        val leftovers = cacheDirFile.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue("no .tmp files should remain after successful generate", leftovers.isEmpty())
    }

    @Test
    fun `generate returns null and leaves no file when source uri is invalid`() = runTest {
        val result = cache.generate("vault-42", Uri.parse("file:///nonexistent.png"))

        assertNull(result)
        assertNull(cache.pathFor("vault-42"))
        val cacheDirFile = File(context.filesDir, ArtworkThumbnailCache.CACHE_DIR_NAME)
        val files = cacheDirFile.listFiles()?.toList() ?: emptyList()
        assertTrue("no files should be left in cache dir", files.isEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `generate produces a thumb under real bitmap decoding`() = runTest {
        // Robolectric's legacy graphics mode returns a stub bitmap even when
        // BitmapFactory is asked for bounds-only — which masked a bug where
        // readSourceBounds Elvis-chained off the use-block's null return and
        // mis-classified every successful bounds read as a stream-open failure.
        // NATIVE mode runs real decode logic, so it catches that regression.
        val result = cache.generate("vault-native", Uri.fromFile(sourceFile))
        assertNotNull("generate should succeed under NATIVE graphics", result)
        assertTrue(result!!.length() > 0)
        val decoded = android.graphics.BitmapFactory.decodeFile(result.absolutePath)
        assertNotNull("written thumb should decode", decoded)
        assertEquals(
            "thumb should be downsampled to exactly TARGET_WIDTH_PX",
            ArtworkThumbnailCache.TARGET_WIDTH_PX,
            decoded.width
        )
        decoded.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `generate leaves no tmp file when source is unreadable`() = runTest {
        // Create a "file" that's empty — openInputStream succeeds but
        // BitmapFactory.decodeStream returns null because there are no
        // valid image bytes. This exercises the silent-null path that
        // skips the outer catch.
        val emptyFile = File.createTempFile("empty-art", ".png", context.cacheDir)
        try {
            val result = cache.generate("vault-42", Uri.fromFile(emptyFile))
            assertNull(result)
            assertNull(cache.pathFor("vault-42"))
            val cacheDirFile = File(context.filesDir, ArtworkThumbnailCache.CACHE_DIR_NAME)
            val leftovers = cacheDirFile.listFiles()?.toList() ?: emptyList()
            assertTrue(
                "no files (incl. .tmp) should remain after silent-null source: $leftovers",
                leftovers.isEmpty()
            )
        } finally {
            emptyFile.delete()
        }
    }

    @Test
    fun `pathFor ignores a thumb sitting in the legacy directory`() = runTest {
        val legacy = File(context.filesDir, "carousel-thumbs")
        legacy.mkdirs()
        File(legacy, "vault-42.webp").writeBytes(byteArrayOf(1, 2, 3))

        assertNull(
            "a 480px thumb from the old cache generation must not be served",
            cache.pathFor("vault-42")
        )
    }

    @Test
    fun `generate removes the legacy directory`() = runTest {
        val legacy = File(context.filesDir, "carousel-thumbs")
        legacy.mkdirs()
        File(legacy, "vault-old.webp").writeBytes(byteArrayOf(1, 2, 3))

        cache.generate("vault-42", Uri.fromFile(sourceFile))

        assertFalse("legacy cache directory should be deleted", legacy.exists())
        assertNotNull("new-generation thumb should still be written", cache.pathFor("vault-42"))
    }
}
