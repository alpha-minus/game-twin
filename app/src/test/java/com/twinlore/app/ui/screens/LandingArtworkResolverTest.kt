package com.twinlore.app.ui.screens

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LandingArtworkResolverTest {

    private lateinit var context: Context
    private lateinit var guideDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        guideDir = File(context.cacheDir, "landing/test-vault").apply { mkdirs() }
        guideDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun null_path_returns_null() {
        assertNull(resolveLandingArtworkFile(context, null, guideDir))
    }

    @Test
    fun blank_path_returns_null() {
        assertNull(resolveLandingArtworkFile(context, "   ", guideDir))
    }

    @Test
    fun file_uri_copies_into_guide_dir() {
        val source = File(context.cacheDir, "src-art.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val out = resolveLandingArtworkFile(context, "file://${source.absolutePath}", guideDir)
        assertNotNull(out)
        assertEquals("src-art.png", out!!.name)
        assertEquals(guideDir, out.parentFile)
        assertTrue(out.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun file_uri_skips_copy_when_target_is_newer() {
        val source = File(context.cacheDir, "src-skip.png").apply { writeBytes(byteArrayOf(1)) }
        val target = File(guideDir, "src-skip.png").apply {
            writeBytes(byteArrayOf(9))
            setLastModified(source.lastModified() + 60_000L)
        }
        val out = resolveLandingArtworkFile(context, "file://${source.absolutePath}", guideDir)
        assertNotNull(out)
        assertTrue(out!!.readBytes().contentEquals(byteArrayOf(9)))
    }

    @Test
    fun file_uri_missing_source_returns_null() {
        val out = resolveLandingArtworkFile(
            context,
            "file://${context.cacheDir.absolutePath}/does-not-exist.png",
            guideDir
        )
        assertNull(out)
    }

    @Test
    fun unsupported_scheme_returns_null() {
        val out = resolveLandingArtworkFile(context, "https://example.com/foo.png", guideDir)
        assertNull(out)
    }

    @Test
    fun file_uri_returns_null_when_target_directory_is_unwritable() {
        val source = File(context.cacheDir, "src-unwritable.png").apply { writeBytes(byteArrayOf(7)) }
        val readOnlyDir = File(context.cacheDir, "landing/read-only").apply {
            mkdirs()
            setWritable(false, false)
        }
        try {
            val out = resolveLandingArtworkFile(
                context,
                "file://${source.absolutePath}",
                readOnlyDir
            )
            assertNull(out)
        } finally {
            readOnlyDir.setWritable(true, false)
        }
    }
}
