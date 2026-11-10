package com.twinlore.app.data.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cache the source menus draw their icons from, fed only by pages the user opens in the
 * built-in browser. What matters legally is what this class does NOT do - fetch anything - so
 * these tests pin the lookup contract: a visit to www.neoseeker.com must satisfy a menu row
 * declared as "neoseeker.com", and a host that was never visited stays null (the UI then shows
 * a monogram instead of asking any network for help).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceIconCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cache = SourceIconCache(context)

    @Test
    fun `a www visit satisfies the bare menu host`() = runTest {
        cache.store("https://www.neoseeker.com/some/page", byteArrayOf(1, 2, 3))

        val f = cache.fileFor("neoseeker.com")
        assertNotNull(f)
        assertEquals(3, f!!.length())
    }

    @Test
    fun `an unvisited host has no icon`() {
        assertNull(cache.fileFor("gamefaqs.gamespot.com"))
    }

    @Test
    fun `garbage input is dropped, not thrown`() = runTest {
        cache.store(null, byteArrayOf(1))
        cache.store("not a url", byteArrayOf(1))
        assertNull(cache.fileFor("not a url"))
        assertNull(cache.fileFor(null))
        assertNull(cache.fileFor("../../../etc/passwd"))
    }

    @Test
    fun `normalization lowercases and strips only the www prefix`() {
        assertEquals("neoseeker.com", SourceIconCache.normalize("WWW.Neoseeker.com"))
        assertEquals("gamefaqs.gamespot.com", SourceIconCache.normalize("gamefaqs.gamespot.com"))
        assertNull(SourceIconCache.normalize("host/with/slashes"))
        assertNull(SourceIconCache.normalize(""))
    }
}
