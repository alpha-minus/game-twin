package com.twinlore.app.ui.fold

import com.twinlore.app.R
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_CLEAR_SEARCH
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_CLOSE_COMMENTS
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_LOADING_COMMENTS
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_LOCKED_ONLY
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_NONE
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_NO_COMMENTS
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_NO_MATCH
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_REFRESH
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_REFRESHING
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_SEARCH
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_SORT_ASC
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_TO_TOP
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_SORT_DESC
import com.twinlore.app.ui.fold.guide.FOLD_AWARDS_UNLINKED
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_LIBRARY_LABEL
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_RETURN_LABEL
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_SETTINGS_LABEL
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_STATUS_TAP
import com.twinlore.app.ui.fold.guide.FOLD_DETECTED_PROMPT
import com.twinlore.app.ui.fold.guide.FOLD_DETECTED_STAY
import com.twinlore.app.ui.fold.guide.FOLD_DETECTED_SUPPORT
import com.twinlore.app.ui.fold.guide.FOLD_DETECTED_SWITCH
import com.twinlore.app.ui.fold.guide.FOLD_INTRO_DISMISS
import com.twinlore.app.ui.fold.guide.FOLD_INTRO_TITLE
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_CANCEL
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_CLOSE
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_CLOSE_NOTE
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_OPEN_APP
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_OPEN_APP_NOTE
import com.twinlore.app.ui.fold.guide.FOLD_HOLD_TITLE
import com.twinlore.app.ui.fold.guide.FOLD_INTRO_HANDLE
import com.twinlore.app.ui.fold.guide.FOLD_INTRO_USE
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_HOLD_NOTE
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_READING_SIZE
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_OPEN_APP
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_READING_SIZE
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_OPEN_APP_NOTE
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_VISIBILITY
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_SIZE
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_NONE
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_OPEN_IN_APP
import com.twinlore.app.ui.fold.guide.FOLD_GUIDE_SWAP_LABEL
import com.twinlore.app.ui.fold.guide.FOLD_IMAGE_MISSING
import com.twinlore.app.ui.fold.guide.FOLD_IMAGE_NEXT
import com.twinlore.app.ui.fold.guide.FOLD_IMAGE_SCREENSHOT
import com.twinlore.app.ui.fold.guide.FOLD_IMAGE_NONE
import com.twinlore.app.ui.fold.guide.FOLD_IMAGE_PREVIOUS
import com.twinlore.app.ui.fold.guide.FOLD_LINK_DISMISS
import com.twinlore.app.ui.fold.guide.FOLD_LINK_UNSUPPORTED
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_GUIDE_THEME
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_OFF
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_ON
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_SMART_FORMAT
import com.twinlore.app.ui.fold.guide.FOLD_SETTINGS_TEXT_SIZE
import com.twinlore.app.ui.fold.guide.FoldPanelSurface
import com.twinlore.app.ui.screens.FOLD_VISIBILITY_SETTING_LABEL
import com.twinlore.app.ui.screens.FOLD_VISIBILITY_SETTING_SUBTITLE
import com.twinlore.app.ui.screens.FOLD_READING_SIZE_SETTING_LABEL
import com.twinlore.app.ui.screens.FOLD_READING_SIZE_SETTING_SUBTITLE
import com.twinlore.app.ui.screens.FOLD_TAB_SIZE_SETTING_LABEL
import com.twinlore.app.ui.screens.FOLD_TAB_SIZE_SETTING_SUBTITLE
import com.twinlore.app.ui.screens.OVERLAY_SETTINGS_HEADER
import com.twinlore.app.ui.screens.OVERLAY_SETTINGS_EXPLANATION
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Project convention: user-facing copy carries no em dashes. Easy to
 * reintroduce by hand, so it gets a test.
 *
 * Not all of this feature's copy is a string resource: the Settings row's
 * label is a Kotlin literal, because the Settings screen spells its labels
 * inline throughout. It is a named constant precisely so this guard can reach
 * it without scraping source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldSettingsCopyTest {

    private val strings: List<Int> = listOf(
        R.string.fold_permission_title,
        R.string.fold_permission_body,
        R.string.fold_permission_continue,
        R.string.fold_permission_dismiss,
        R.string.fold_button_description,
        R.string.fold_handle_description,
        R.string.fold_swap_hint_title,
        R.string.fold_swap_hint_body,
        R.string.fold_swap_hint_continue,
        R.string.fold_swap_none,
        R.string.fold_overlay_hint_title,
        R.string.fold_overlay_hint_body,
        R.string.fold_overlay_hint_dismiss,
    )

    @Test
    fun `fold copy exists and avoids em dashes`() {
        val context = RuntimeEnvironment.getApplication()
        strings.forEach { id ->
            val text = context.getString(id)
            assertTrue("string $id should not be blank", text.isNotBlank())
            assertFalse("string $id contains an em dash: $text", text.contains('—'))
        }
    }

    /**
     * The hint is the only place the hold is taught. It used to promise that
     * holding opened the overlay over the other screen; the hold moves the app
     * itself now, and a hint describing the old gesture is worse than none.
     */
    @Test
    fun `the swap hint teaches the hold as a screen switch`() {
        val text = RuntimeEnvironment.getApplication().getString(R.string.fold_swap_hint_body)
        assertTrue("the hint must name the gesture: $text", text.contains("Hold"))
        assertTrue("the hint must say what the hold does: $text", text.contains("switch screen"))
    }

    @Test
    fun `the hardcoded fold literals exist and avoid em dashes`() {
        val literals: List<String> = listOf(
            OVERLAY_SETTINGS_HEADER,
            OVERLAY_SETTINGS_EXPLANATION,
            FOLD_VISIBILITY_SETTING_LABEL,
            FOLD_VISIBILITY_SETTING_SUBTITLE,
            FOLD_TAB_SIZE_SETTING_LABEL,
            FOLD_TAB_SIZE_SETTING_SUBTITLE,
            FOLD_READING_SIZE_SETTING_LABEL,
            FOLD_READING_SIZE_SETTING_SUBTITLE,
            FOLD_SETTINGS_SIZE,
            FOLD_SETTINGS_READING_SIZE,
            FOLD_SETTINGS_VISIBILITY,
            FOLD_SETTINGS_HOLD_NOTE,
            FOLD_SETTINGS_OPEN_APP,
            FOLD_SETTINGS_OPEN_APP_NOTE,
            FOLD_GUIDE_RETURN_LABEL,
            FOLD_GUIDE_LIBRARY_LABEL,
            FOLD_GUIDE_SETTINGS_LABEL,
            FOLD_GUIDE_STATUS_TAP,
            FOLD_DETECTED_PROMPT,
            FOLD_DETECTED_SWITCH,
            FOLD_DETECTED_STAY,
            FOLD_DETECTED_SUPPORT,
            FOLD_INTRO_TITLE,
            FOLD_INTRO_USE,
            FOLD_INTRO_HANDLE,
            FOLD_HOLD_TITLE,
            FOLD_HOLD_OPEN_APP,
            FOLD_HOLD_OPEN_APP_NOTE,
            FOLD_HOLD_CLOSE,
            FOLD_HOLD_CLOSE_NOTE,
            FOLD_HOLD_CANCEL,
            FOLD_INTRO_DISMISS,
            FOLD_GUIDE_SWAP_LABEL,
            FOLD_GUIDE_NONE,
            FOLD_GUIDE_OPEN_IN_APP,
            FOLD_IMAGE_NONE,
            FOLD_IMAGE_MISSING,
            FOLD_IMAGE_PREVIOUS,
            FOLD_IMAGE_NEXT,
            FOLD_IMAGE_SCREENSHOT,
            FOLD_LINK_UNSUPPORTED,
            FOLD_LINK_DISMISS,
            FOLD_SETTINGS_TEXT_SIZE,
            FOLD_SETTINGS_GUIDE_THEME,
            FOLD_SETTINGS_SMART_FORMAT,
            FOLD_SETTINGS_ON,
            FOLD_SETTINGS_OFF,
            FOLD_AWARDS_NONE,
            FOLD_AWARDS_UNLINKED,
            FOLD_AWARDS_NO_MATCH,
            FOLD_AWARDS_REFRESH,
            FOLD_AWARDS_REFRESHING,
            FOLD_AWARDS_LOCKED_ONLY,
            FOLD_AWARDS_SORT_ASC,
            FOLD_AWARDS_SORT_DESC,
            FOLD_AWARDS_NO_COMMENTS,
            FOLD_AWARDS_LOADING_COMMENTS,
            FOLD_AWARDS_CLOSE_COMMENTS,
            FOLD_AWARDS_SEARCH,
            FOLD_AWARDS_CLEAR_SEARCH,
            FOLD_AWARDS_TO_TOP,
        ) + FoldPanelSurface.entries.map { it.label }

        literals.forEach { text ->
            assertTrue("a fold literal should not be blank", text.isNotBlank())
            assertFalse("literal contains an em dash: $text", text.contains('—'))
        }
    }

}
