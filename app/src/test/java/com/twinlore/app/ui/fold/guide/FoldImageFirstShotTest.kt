package com.twinlore.app.ui.fold.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldImageFirstShotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the first shot in an empty vault replaces the empty surface`() {
        var images by mutableStateOf(emptyList<FoldImage>())
        var capturing by mutableStateOf(false)
        compose.setContent {
            FoldImageContent(
                images = images,
                index = 0,
                vaultId = "v",
                chromeVisible = true,
                bottomBarHeight = 56.dp,
                capturing = capturing,
                view = null,
                onPrevious = {},
                onNext = {},
                onToggleChrome = {},
                onOpenApp = {},
                onTakeScreenshot = {},
                onViewChanged = {},
            )
        }
        capturing = true
        compose.waitForIdle()
        images = listOf(FoldImage("a", "Shot", uri = null))
        capturing = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription(FOLD_IMAGE_DELETE).assertExists()
        images = emptyList()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(FOLD_IMAGE_DELETE).assertDoesNotExist()
    }
}
