package com.twinlore.app.ui.screens.offline

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuildMhtAssetInterceptorTest {

    @Test
    fun returnsNullWhenManifestEmpty() {
        val manifest = mutableStateOf<Map<String, String>>(emptyMap())
        val baseDir = mutableStateOf<File?>(File("/dev/null"))
        val interceptor = buildMhtAssetInterceptor(manifest, baseDir)
        val resp = interceptor("https://example.com/x.png")
        assertNull(resp)
    }

    @Test
    fun returnsNullWhenBaseDirNull() {
        val manifest = mutableStateOf(mapOf("https://example.com/x.png" to "assets/x.png"))
        val baseDir = mutableStateOf<File?>(null)
        val interceptor = buildMhtAssetInterceptor(manifest, baseDir)
        assertNull(interceptor("https://example.com/x.png"))
    }

    @Test
    fun returnsNullWhenUrlNotInManifest() {
        val manifest = mutableStateOf(mapOf("https://example.com/a.png" to "assets/a.png"))
        val baseDir = mutableStateOf<File?>(File("/dev/null"))
        val interceptor = buildMhtAssetInterceptor(manifest, baseDir)
        assertNull(interceptor("https://example.com/b.png"))
    }

    @Test
    fun returnsResponseForResolvedUrl() {
        val tmp = File.createTempFile("interceptor-asset", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            deleteOnExit()
        }
        val manifest = mutableStateOf(mapOf("https://example.com/a.png" to tmp.name))
        val baseDir = mutableStateOf<File?>(tmp.parentFile)
        val interceptor = buildMhtAssetInterceptor(manifest, baseDir)
        val resp = interceptor("https://example.com/a.png")
        assertNotNull(resp)
        assertNotNull("mime type should be resolved or fall back", resp!!.mimeType)
    }

    @Test
    fun returnsNullWhenFileMissingOnDisk() {
        val manifest = mutableStateOf(mapOf("https://example.com/a.png" to "missing.png"))
        val baseDir = mutableStateOf<File?>(File("/tmp"))
        val interceptor = buildMhtAssetInterceptor(manifest, baseDir)
        assertNull(interceptor("https://example.com/a.png"))
    }
}
