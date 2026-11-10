package com.twinlore.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a Steam profile is connected, the app suggests the user's own Web API key.
 *
 * Steam only dates owned games for the key's owner, so on the bundled key
 * detection falls back to the undated recently played list, and an undated
 * Steam game can never beat a dated RetroAchievements one.
 */
class SteamOwnKeyPromptTest {

    @Test
    fun `suggested while the bundled key is in use`() {
        assertNotNull(steamOwnKeyPromptFor(apiKeyOverride = "", raConnected = false))
        assertNotNull(steamOwnKeyPromptFor(apiKeyOverride = "   ", raConnected = false))
    }

    @Test
    fun `not suggested once the user has a key of their own`() {
        assertNull(steamOwnKeyPromptFor(apiKeyOverride = "ABCDEF", raConnected = true))
    }

    @Test
    fun `retroachievements users are told why it matters to them`() {
        val withRa = steamOwnKeyPromptFor(apiKeyOverride = "", raConnected = true)!!
        val withoutRa = steamOwnKeyPromptFor(apiKeyOverride = "", raConnected = false)!!
        assertTrue(withRa.body.contains("RetroAchievements"))
        assertFalse(withoutRa.body.contains("RetroAchievements"))
    }

    @Test
    fun `the copy has no em dash`() {
        listOf(true, false).forEach { ra ->
            val prompt = steamOwnKeyPromptFor(apiKeyOverride = "", raConnected = ra)!!
            assertFalse(prompt.title.contains('—'))
            assertFalse(prompt.body.contains('—'))
        }
    }
}
