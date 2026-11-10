package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The achievements surface is opaque.
 *
 * The panel's surfaces dissolve into each other: the incoming one fades in on
 * top while the outgoing one stays put underneath, so a surface that paints
 * no ground of its own is a window onto whatever it is replacing for the
 * length of the fade. Recorded on the Thor: the list fading in with the map
 * still fully visible through it, and the map then dropping out 300ms later.
 * A ground makes that the plain crossfade it was meant to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldAchievementsGroundTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `nothing behind the list shows through it`() {
        compose.setContent {
            // A loud backdrop, standing in for the surface being left.
            Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {
                FoldAchievementsContent(
                    awards = FoldAwards(linked = true),
                    loading = false,
                    refreshing = false,
                    typeFilter = RaAchievementTypeFilter.ALL,
                    lockedOnly = false,
                    sortField = RaAchievementSortField.UNLOCK_TIME,
                    sortDirection = SortDirection.DESC,
                    steamSortField = com.twinlore.app.domain.SteamAchievementSortField.UNLOCK_TIME,
                    steamSortDirection = SortDirection.DESC,
                    commentsFor = null,
                    comments = emptyList(),
                    commentsLoading = false,
                    onRefresh = {},
                    onCycleTypeFilter = {},
                    onToggleLockedOnly = {},
                    onCycleSort = {},
                    onToggleSortDirection = {},
                    onShowComments = {},
                    onDismissComments = {},
                    onBlockedLink = {},
                    searchQuery = "",
                    onSearchChanged = {},
                    onWantsKeyboard = {},
                    onToggleChrome = {},
                    onOpenApp = {},
                    scroll = null,
                    onScrollChanged = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        // Drawn by hand into a bitmap: PixelCopy never completes under
        // Robolectric, and a software canvas is what native graphics mode is for.
        val corner = compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(
                view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888,
            )
            view.draw(android.graphics.Canvas(bitmap))
            // A corner of the content, where the empty message is not. Only
            // the ground is there.
            val content = view.findViewById<android.view.View>(android.R.id.content)
            val at = IntArray(2).also { content.getLocationInWindow(it) }
            bitmap.getPixel(at[0] + 2, at[1] + 2)
        }
        assertNotEquals("the backdrop must not show through the surface", android.graphics.Color.RED, corner)
    }
}
