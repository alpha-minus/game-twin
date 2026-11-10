package com.twinlore.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapThumbnailCacheTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var cache: MapThumbnailCache
    private lateinit var cacheDir: File
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = tempDir.newFolder("map-thumbs")
        cache = MapThumbnailCache(context, { cacheDir })
        // Write a valid PNG bitmap into a temp file to serve as the source.
        sourceFile = tempDir.newFile("sample.png")
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        sourceFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    @After
    fun tearDown() {
        // TemporaryFolder rule handles cleanup.
    }

    // -------------------------------------------------------------------------
    // Core (non-concurrency) tests
    // -------------------------------------------------------------------------

    @Test
    fun `pathFor returns null when no thumb exists`() {
        assertNull(cache.pathFor("map-resource-1"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `generate writes a webp and pathFor returns it`() {
        val result = cache.generate("map-resource-1", Uri.fromFile(sourceFile))

        assertNotNull("generate should return the thumb File on success", result)
        assertTrue("thumb file should exist on disk", result!!.exists())
        assertTrue("thumb file should be non-empty", result.length() > 0)

        val fromPath = cache.pathFor("map-resource-1")
        assertNotNull("pathFor should return the cached file", fromPath)
        assertTrue(fromPath!!.exists())

        val decoded = android.graphics.BitmapFactory.decodeFile(result.absolutePath)
        assertNotNull("thumb should decode to a valid bitmap", decoded)
        assertTrue("thumb width ${decoded.width} should be <= 512", decoded.width <= 512)
        decoded.recycle()
    }

    @Test
    fun `pathFor returns null when no thumb exists (non-native mode)`() {
        assertNull(cache.pathFor("resource-not-there"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `invalidate removes the file`() {
        cache.generate("map-resource-2", Uri.fromFile(sourceFile))
        assertNotNull(cache.pathFor("map-resource-2"))

        cache.invalidate("map-resource-2")

        assertNull(cache.pathFor("map-resource-2"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `wipeAll empties the directory`() {
        cache.generate("map-resource-3", Uri.fromFile(sourceFile))
        cache.generate("map-resource-4", Uri.fromFile(sourceFile))
        cache.generate("map-resource-5", Uri.fromFile(sourceFile))

        cache.wipeAll()

        assertNull(cache.pathFor("map-resource-3"))
        assertNull(cache.pathFor("map-resource-4"))
        assertNull(cache.pathFor("map-resource-5"))
        val remaining = cacheDir.listFiles()?.toList() ?: emptyList()
        assertTrue("cache dir should be empty after wipeAll, found: $remaining", remaining.isEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `generate cleans up tmp file on decode failure with 0-byte source`() {
        val emptyFile = tempDir.newFile("empty-source.png")
        // File is 0 bytes; BitmapFactory will fail to decode it.
        val result = cache.generate("map-resource-bad", Uri.fromFile(emptyFile))

        assertNull("generate should return null for an undecodable source", result)
        assertNull(cache.pathFor("map-resource-bad"))
        val leftovers = cacheDir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue("no .tmp files should remain after failed generate: $leftovers", leftovers.isEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `invalidateAll removes multiple files`() {
        cache.generate("map-resource-A", Uri.fromFile(sourceFile))
        cache.generate("map-resource-B", Uri.fromFile(sourceFile))
        cache.generate("map-resource-C", Uri.fromFile(sourceFile))

        cache.invalidateAll(listOf("map-resource-A", "map-resource-C"))

        assertNull(cache.pathFor("map-resource-A"))
        assertNotNull(cache.pathFor("map-resource-B"))
        assertNull(cache.pathFor("map-resource-C"))
    }

    @Test
    fun `generate returns null for non-existent source uri`() {
        val result = cache.generate("map-resource-x", Uri.parse("file:///does/not/exist.png"))
        assertNull(result)
        assertNull(cache.pathFor("map-resource-x"))
    }

    @Test
    fun `invalidate is a no-op when file does not exist`() {
        // Should not throw
        cache.invalidate("non-existent-resource")
        assertNull(cache.pathFor("non-existent-resource"))
    }

    // -------------------------------------------------------------------------
    // Concurrency tests
    // -------------------------------------------------------------------------

    /**
     * Test 7: Dedup. 8 parallel getOrGenerate calls for the same id should invoke the
     * sourceProvider exactly once.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `getOrGenerate deduplicates concurrent callers, sourceProvider called once`() {
        // Use a real scope backed by IO because TestScope + NATIVE graphics + Mutex can deadlock.
        val realScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val concurrentCache = MapThumbnailCache(context, { cacheDir }, realScope)

        val callCount = AtomicInteger(0)
        val sourceUri = Uri.fromFile(sourceFile)

        // Launch 8 concurrent callers and block until all complete.
        val results = kotlinx.coroutines.runBlocking {
            (1..8).map {
                async {
                    concurrentCache.getOrGenerate("dedup-id") {
                        callCount.incrementAndGet()
                        sourceUri
                    }
                }
            }.awaitAll()
        }

        // All callers should get a non-null result (the thumb was generated).
        results.forEach { f ->
            assertNotNull("each caller should receive the cached file", f)
        }
        // sourceProvider should have been called at most once (the winner of the dedup race).
        assertTrue(
            "sourceProvider call count should be <= 1 (was ${callCount.get()})",
            callCount.get() <= 1
        )
    }

    /**
     * Test 8: getOrGenerate with sourceProvider returning null returns null and removes inFlight.
     */
    @Test
    fun `getOrGenerate with null sourceProvider returns null and cleans up inFlight`() {
        val result = kotlinx.coroutines.runBlocking {
            cache.getOrGenerate("null-source-id") { null }
        }
        assertNull("getOrGenerate should return null when sourceProvider returns null", result)
        assertNull(cache.pathFor("null-source-id"))
    }

    /**
     * Test 9: Fast-path: if the thumb exists before getOrGenerate is called, sourceProvider
     * is never invoked.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `getOrGenerate returns existing file without calling sourceProvider`() {
        // Pre-populate the cache.
        val preGenerated = cache.generate("preexist-id", Uri.fromFile(sourceFile))
        assertNotNull(preGenerated)

        val providerCalled = AtomicInteger(0)
        val result = kotlinx.coroutines.runBlocking {
            cache.getOrGenerate("preexist-id") {
                providerCalled.incrementAndGet()
                Uri.fromFile(sourceFile)
            }
        }

        assertNotNull(result)
        assertEquals("sourceProvider should not be called when cache already has the file", 0, providerCalled.get())
    }

    /**
     * Test 10: warmGenerateAsync fires and eventually populates the cache.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `warmGenerateAsync eventually populates the cache`() {
        // warmGenerateAsync is fire-and-forget; we need to wait for it to finish.
        // Use a real blocking approach: launch via runBlocking wrapping.
        val realScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val asyncCache = MapThumbnailCache(context, { cacheDir }, realScope)

        asyncCache.warmGenerateAsync("warm-id") { Uri.fromFile(sourceFile) }

        // Poll briefly; warmGenerateAsync schedules on the IO scope.
        val deadline = System.currentTimeMillis() + 5_000
        while (asyncCache.pathFor("warm-id") == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertNotNull(
            "warmGenerateAsync should eventually write the thumb",
            asyncCache.pathFor("warm-id")
        )
    }
}
