package com.twinlore.app.data.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AiSessionStoreTest {
    private lateinit var context: Context
    private lateinit var store: AiSessionStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = AiSessionStore(context)
    }

    @Test
    fun load_returnsEmptySession_whenNothingSaved() {
        val session = store.load("vault-1")
        assertEquals("vault-1", session.vaultId)
        assertTrue(session.messages.isEmpty())
    }

    @Test
    fun saveThenLoad_roundTripsMessages() {
        val session = AiSession(
            vaultId = "vault-1",
            messages = listOf(
                AiMessage(AiRole.USER, "where is the hidden room?", imagePath = "/tmp/a.png", timestamp = 5L),
                AiMessage(AiRole.MODEL, "behind the waterfall", timestamp = 6L),
            ),
        )
        store.save(session)
        val loaded = store.load("vault-1")
        assertEquals(2, loaded.messages.size)
        assertEquals(AiRole.USER, loaded.messages[0].role)
        assertEquals("where is the hidden room?", loaded.messages[0].text)
        assertEquals("/tmp/a.png", loaded.messages[0].imagePath)
        assertEquals(AiRole.MODEL, loaded.messages[1].role)
    }

    @Test
    fun sessions_areIsolatedPerVault() {
        store.save(AiSession("vault-1", listOf(AiMessage(AiRole.USER, "a"))))
        store.save(AiSession("vault-2", listOf(AiMessage(AiRole.USER, "b"))))
        assertEquals("a", store.load("vault-1").messages[0].text)
        assertEquals("b", store.load("vault-2").messages[0].text)
    }

    @Test
    fun clear_removesJsonAndImages() {
        store.save(AiSession("vault-1", listOf(AiMessage(AiRole.USER, "a"))))
        val imgDir = store.imageDir("vault-1")
        imgDir.mkdirs()
        File(imgDir, "x.png").writeBytes(byteArrayOf(1, 2, 3))
        store.clear("vault-1")
        assertFalse(File(context.filesDir, "ai_assistant/sessions/vault-1.json").exists())
        assertTrue(store.load("vault-1").messages.isEmpty())
        assertFalse(File(imgDir, "x.png").exists())
    }

    @Test
    fun copyImageIntoSession_returnsPathInsideImageDir() {
        val src = File(context.cacheDir, "src.png").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val dest = store.copyImageIntoSession("vault-1", src, 42L)
        assertTrue(dest.startsWith(store.imageDir("vault-1").absolutePath))
        assertTrue(File(dest).readBytes().contentEquals(byteArrayOf(9, 9, 9)))
    }
}
