package com.twinlore.app.data

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllFilesAccessPromptDecisionTest {

    private val r = Build.VERSION_CODES.R

    @Test
    fun `prompts a large library that has not granted or declined`() {
        assertTrue(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = false, vaultCount = 13, declined = false
            )
        )
    }

    @Test
    fun `never prompts when access is already granted`() {
        assertFalse(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = true, vaultCount = 500, declined = false
            )
        )
    }

    @Test
    fun `never prompts again once declined`() {
        assertFalse(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = false, vaultCount = 500, declined = true
            )
        )
    }

    @Test
    fun `does not prompt at exactly the threshold`() {
        assertFalse(
            "the rule is MORE than ten vaults, not at least ten",
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = false,
                vaultCount = AllFilesAccessPromptDecision.VAULT_THRESHOLD, declined = false
            )
        )
        assertTrue(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = false,
                vaultCount = AllFilesAccessPromptDecision.VAULT_THRESHOLD + 1, declined = false
            )
        )
    }

    @Test
    fun `never prompts below Android R where the permission does not exist`() {
        assertFalse(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = Build.VERSION_CODES.Q, granted = false, vaultCount = 500, declined = false
            )
        )
    }

    @Test
    fun `empty library does not prompt`() {
        assertFalse(
            AllFilesAccessPromptDecision.shouldPrompt(
                sdkInt = r, granted = false, vaultCount = 0, declined = false
            )
        )
    }
}
