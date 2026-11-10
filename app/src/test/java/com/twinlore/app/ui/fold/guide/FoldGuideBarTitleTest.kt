package com.twinlore.app.ui.fold.guide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the panel's top bar is naming.
 *
 * On the guide surface it is the guide, which is also what the app's bar shows.
 * On the image surface it was still the guide, which meant the one surface
 * where the content has no caption of its own was also the one where the bar
 * refused to say what you were looking at.
 */
class FoldGuideBarTitleTest {

    private val images = listOf(
        FoldImage(id = "a", title = "Dracula's Castle", uri = "file:///a.png"),
        FoldImage(id = "b", title = "Abandoned Mine", uri = "file:///b.png"),
        FoldImage(id = "c", title = "Clock Tower", uri = "file:///c.png"),
    )

    private fun state(surface: FoldPanelSurface, index: Int = 0) = FoldGuideUiState(
        surface = surface,
        title = "Castlevania: Dawn of Sorrow",
        images = images,
        imageIndex = index,
    )

    @Test
    fun `the guide surface names the guide`() {
        assertEquals(
            "Castlevania: Dawn of Sorrow",
            state(FoldPanelSurface.GUIDE).barTitle,
        )
    }

    // The count is the other half of it: paging with the chevrons is the only
    // way through these, and without it there is nothing on screen that says
    // how many are left.
    @Test
    fun `the image surface names the image and where it sits`() {
        assertEquals("Dracula's Castle  1/3", state(FoldPanelSurface.IMAGE, 0).barTitle)
        assertEquals("Clock Tower  3/3", state(FoldPanelSurface.IMAGE, 2).barTitle)
    }

    @Test
    fun `a lone image carries no count`() {
        val single = state(FoldPanelSurface.IMAGE).copy(images = images.take(1))
        assertEquals("Dracula's Castle", single.barTitle)
    }

    // A vault whose images were never named, and one with no images at all.
    // Both fall back to the guide rather than leaving the bar empty.
    @Test
    fun `an unnamed or absent image falls back to the guide`() {
        val unnamed = state(FoldPanelSurface.IMAGE)
            .copy(images = listOf(FoldImage(id = "a", title = "  ", uri = null)))
        assertEquals("Castlevania: Dawn of Sorrow", unnamed.barTitle)

        val none = state(FoldPanelSurface.IMAGE).copy(images = emptyList())
        assertEquals("Castlevania: Dawn of Sorrow", none.barTitle)
    }
}
