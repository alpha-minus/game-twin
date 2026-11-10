package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bin opposite the shutter, and the question it asks.
 *
 * It sits where a thumb rests, on a surface where one tap already hides the
 * chrome and another takes a picture, so the one thing it must never do is
 * delete on the tap. The tap asks; the prompt answers; the prompt is drawn in
 * the panel's own tree because this window cannot host a Dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldImageDeleteTest {

    @get:Rule
    val compose = createComposeRule()

    // A row without a file still shows the bin: a picture that is not on the
    // device is exactly the one you want out of the list.
    private val image = FoldImage("a", "Overworld", uri = null)

    private fun content(
        images: List<FoldImage>,
        chromeVisible: Boolean = true,
        onDelete: () -> Unit = {},
    ) {
        compose.setContent {
            FoldImageContent(
                images = images,
                index = 0,
                vaultId = "v",
                chromeVisible = chromeVisible,
                bottomBarHeight = 56.dp,
                capturing = false,
                view = null,
                onPrevious = {},
                onNext = {},
                onToggleChrome = {},
                onOpenApp = {},
                onTakeScreenshot = {},
                onDeleteImage = onDelete,
                onViewChanged = {},
            )
        }
    }

    @Test
    fun `the bin asks and does nothing else`() {
        var asked = 0
        content(listOf(image), onDelete = { asked++ })
        compose.onNodeWithContentDescription(FOLD_IMAGE_DELETE).assertIsDisplayed().performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `the empty surface has no bin`() {
        content(emptyList())
        compose.onNodeWithContentDescription(FOLD_IMAGE_DELETE).assertDoesNotExist()
        // The shutter is still there: the first shot is what ends the empty state.
        compose.onNodeWithContentDescription(FOLD_IMAGE_SCREENSHOT).assertIsDisplayed()
    }

    @Test
    fun `the bin goes with the chrome`() {
        content(listOf(image), chromeVisible = false)
        compose.onNodeWithContentDescription(FOLD_IMAGE_DELETE).assertDoesNotExist()
    }

    @Test
    fun `the question names the image and each answer is its own button`() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            FoldDeleteImagePrompt(
                imageTitle = "Overworld",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true },
            )
        }
        compose.onNodeWithText(FOLD_DELETE_TITLE).assertIsDisplayed()
        compose.onNodeWithText("Overworld").assertIsDisplayed()
        compose.onNodeWithTag("fold_delete_cancel").performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
        compose.onNodeWithTag("fold_delete_confirm").performClick()
        assertTrue(confirmed)
    }

    // The scrim is not an answer: a press that missed the card must not close
    // the question, and certainly must not delete.
    @Test
    fun `a press on the scrim answers nothing`() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            FoldDeleteImagePrompt(
                imageTitle = "Overworld",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true },
            )
        }
        compose.onNodeWithTag("fold_delete_prompt").performClick()
        assertFalse(confirmed)
        assertFalse(dismissed)
    }
}
