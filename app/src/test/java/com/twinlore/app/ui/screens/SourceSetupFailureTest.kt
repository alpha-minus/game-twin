package com.twinlore.app.ui.screens

import com.twinlore.app.data.LastPlayedSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSetupFailureTest {

    @Test
    fun `first setup with no method saved discards a bad pick`() {
        assertTrue(discardsFailedSourceSetup(LastPlayedSource.NOT_CONFIGURED, libraryEmpty = false))
    }

    @Test
    fun `empty library discards a bad pick even with a method saved`() {
        assertTrue(discardsFailedSourceSetup(LastPlayedSource.ES_DE, libraryEmpty = true))
    }

    @Test
    fun `configured method with games keeps the pick and shows the help dialog`() {
        assertFalse(discardsFailedSourceSetup(LastPlayedSource.RETROARCH, libraryEmpty = false))
    }

    @Test
    fun `failure message names the launcher and avoids em dashes`() {
        listOf(LastPlayedSource.RETROARCH, LastPlayedSource.ES_DE, LastPlayedSource.COCOON).forEach { source ->
            val message = sourceSetupFailureMessage(source)
            assertTrue(message, message.contains("not successful"))
            assertFalse(message, message.contains('—'))
        }
        assertTrue(sourceSetupFailureMessage(LastPlayedSource.RETROARCH).startsWith("RetroArch"))
    }
}
