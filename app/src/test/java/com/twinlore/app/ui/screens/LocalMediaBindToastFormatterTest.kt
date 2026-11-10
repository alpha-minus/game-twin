package com.twinlore.app.ui.screens

import com.twinlore.app.data.library.BindReport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the toast-string formatter used by `LibraryViewModel.rescanLocalMediaForVault`.
 * Covers the three branches: newly linked / alternatives only / no matches.
 */
class LocalMediaBindToastFormatterTest {

    @Test
    fun newly_linked_artwork_only() {
        val report = BindReport(
            vaultId = "v1",
            artworkBound = "cover.png",
            guideBound = null,
            artworkAvailable = 1,
            guideAvailable = 0,
        )
        assertEquals(
            "Linked artwork to Resident Evil 4",
            formatLocalMediaBindToast(report, "Resident Evil 4"),
        )
    }

    @Test
    fun newly_linked_artwork_and_guide() {
        val report = BindReport(
            vaultId = "v1",
            artworkBound = "cover.png",
            guideBound = "manual.pdf",
            artworkAvailable = 1,
            guideAvailable = 1,
        )
        assertEquals(
            "Linked artwork and guide to Resident Evil 4",
            formatLocalMediaBindToast(report, "Resident Evil 4"),
        )
    }

    @Test
    fun newly_linked_guide_only() {
        val report = BindReport(
            vaultId = "v1",
            artworkBound = null,
            guideBound = "manual.pdf",
            artworkAvailable = 0,
            guideAvailable = 1,
        )
        assertEquals(
            "Linked guide to Resident Evil 4",
            formatLocalMediaBindToast(report, "Resident Evil 4"),
        )
    }

    @Test
    fun alternatives_available_but_no_new_link() {
        // Slot was already filled (or strict-match auto-bind chose to preserve);
        // matcher returned candidates but none became newly bound.
        val report = BindReport(
            vaultId = "v1",
            artworkBound = null,
            guideBound = null,
            artworkAvailable = 3,
            guideAvailable = 0,
        )
        assertEquals(
            "No new files to link for Resident Evil 4 (alternatives in picker)",
            formatLocalMediaBindToast(report, "Resident Evil 4"),
        )
    }

    @Test
    fun no_matches_at_all() {
        val report = BindReport(
            vaultId = "v1",
            artworkBound = null,
            guideBound = null,
            artworkAvailable = 0,
            guideAvailable = 0,
        )
        assertEquals(
            "No local matches found for Resident Evil 4",
            formatLocalMediaBindToast(report, "Resident Evil 4"),
        )
    }
}
