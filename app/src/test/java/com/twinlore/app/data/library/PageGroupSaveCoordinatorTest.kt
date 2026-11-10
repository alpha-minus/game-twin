package com.twinlore.app.data.library

import com.twinlore.app.ui.screens.browser.BulkSaveState
import com.twinlore.app.ui.screens.browser.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PageGroupSaveCoordinatorTest {

    // Parameter typed as base CoroutineDispatcher so the kapt stub generator
    // can resolve the signature without losing the StandardTestDispatcher
    // import. (Using StandardTestDispatcher in a private member signature
    // drops the import from the generated Java stub.)
    private fun newCoordinator(
        dispatcher: CoroutineDispatcher,
        sink: FakeGroupRepoSink,
        cacheDir: File = File.createTempFile("pgcoord", "").apply { delete(); mkdirs() }
    ): PageGroupSaveCoordinator = PageGroupSaveCoordinator(
        save = sink,
        mainDispatcher = dispatcher,
        politenessDelayMs = { 0L },
        loadTimeoutMs = 1_000L,
        cacheDir = cacheDir
    )

    @Test fun happy_path_creates_one_group_with_all_files() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = FakePageGroupDriver(succeed = setOf("u1", "u2", "u3"))
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        val toc = listOf(
            TocEntry("Index",     "u1", null),
            TocEntry("Chapter 1", "u2", null),
            TocEntry("Anchor A",  "u2", "#a"),
            TocEntry("Chapter 2", "u3", null)
        )

        c.start(vaultId = "v1", indexUrl = "u1", indexPageTitle = "Title", toc = toc) { driver }
        advanceUntilIdle()

        val done = c.state.value as BulkSaveState.Done
        assertEquals(3, done.saved)
        assertEquals(0, done.skipped)
        assertTrue(done.failed.isEmpty())

        assertNotNull(sink.created)
        val created = sink.created!!
        // 3 unique save targets produced 3 unique resource ids; all 4 ToC
        // entries inherit non-null ids (the u2 anchor entry shares with the
        // u2 chapter entry).
        assertEquals(3, created.entries.mapNotNull { it.mhtResourceId }.toSet().size)
        assertEquals(4, created.entries.count { it.mhtResourceId != null })
        assertEquals(4, created.entries.size)
        assertEquals(setOf("u1", "u2", "u3"), created.entries.map { it.savedHref }.toSet())
        // Both entries pointing at u2 share the same resource id.
        val u2Entries = created.entries.filter { it.savedHref == "u2" }
        assertEquals(1, u2Entries.mapNotNull { it.mhtResourceId }.toSet().size)
    }

    @Test fun per_url_failure_yields_null_mht_for_that_save_target() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = FakePageGroupDriver(succeed = setOf("u1", "u3"), fail = setOf("u2"))
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "X",
            toc = listOf(
                TocEntry("Index",      "u1", null),
                TocEntry("Chapter 1",  "u2", null),
                TocEntry("Anchor",     "u2", "#a"),
                TocEntry("Chapter 2",  "u3", null)
            )
        ) { driver }
        advanceUntilIdle()

        val entries = sink.created!!.entries
        assertNull(entries.first { it.savedHref == "u2" && it.fragment == null }.mhtResourceId)
        assertNull(entries.first { it.savedHref == "u2" && it.fragment == "#a" }.mhtResourceId)
        assertNotNull(entries.first { it.savedHref == "u1" }.mhtResourceId)
        assertNotNull(entries.first { it.savedHref == "u3" }.mhtResourceId)
    }

    @Test fun storage_failure_records_failed_http() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // Driver succeeds for all URLs; sink fails to store u2.
        val driver = FakePageGroupDriver(succeed = setOf("u1", "u2", "u3"))
        val sink = FakeGroupRepoSink(failStageFor = setOf("u2"))
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "X",
            toc = listOf(
                TocEntry("Index",     "u1", null),
                TocEntry("Chapter 1", "u2", null),
                TocEntry("Anchor",    "u2", "#a"),
                TocEntry("Chapter 2", "u3", null)
            )
        ) { driver }
        advanceUntilIdle()

        val done = c.state.value as BulkSaveState.Done
        // u1 and u3 saved; u2 failed
        assertEquals(2, done.saved)
        assertEquals(1, done.failed.size)
        assertEquals("u2", done.failed.first().url)

        val entries = sink.created!!.entries
        // The two u2 entries must carry null mhtResourceId
        assertNull(entries.first { it.savedHref == "u2" && it.fragment == null }.mhtResourceId)
        assertNull(entries.first { it.savedHref == "u2" && it.fragment == "#a" }.mhtResourceId)
        // u1 and u3 entries must carry non-null ids
        assertNotNull(entries.first { it.savedHref == "u1" }.mhtResourceId)
        assertNotNull(entries.first { it.savedHref == "u3" }.mhtResourceId)
    }

    @Test fun cancel_rolls_the_group_back_fully() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = FakePageGroupDriver(succeed = setOf("u1", "u2"), hangOn = "u2")
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "X",
            toc = listOf(TocEntry("A", "u1", null), TocEntry("B", "u2", null))
        ) { driver }
        // Advance enough to start u2's load, then cancel.
        testScheduler.advanceTimeBy(500)
        c.cancel()
        advanceUntilIdle()

        assertNull("no commitGroup expected after cancel", sink.created)
        assertTrue("rollback should be invoked", sink.rolledBackFiles)
    }

    @Test fun anchors_threaded_into_committed_entries() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val anchors = mapOf(
            "u2" to listOf(RawAnchor("Hyrule Castle", 200), RawAnchor("Throne Room", 1400))
        )
        val driver = FakePageGroupDriver(succeed = setOf("u1", "u2"), anchorsByUrl = anchors)
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "X",
            toc = listOf(
                TocEntry("Index", "u1", null),
                TocEntry("Hyrule Castle", "u2", null),
                TocEntry("Throne Room", "u2", "#throne-room")
            )
        ) { driver }
        advanceUntilIdle()

        val u1Entries = sink.created!!.entries.filter { it.savedHref == "u1" }
        val u2Entries = sink.created!!.entries.filter { it.savedHref == "u2" }
        assertTrue(u1Entries.all { it.anchors.isEmpty() })
        assertEquals(2, u2Entries.size)
        // Both u2 entries (the file row + the anchor sub-entry) inherit the same set.
        assertTrue(u2Entries.all { it.anchors.size == 2 })
        assertEquals("Hyrule Castle", u2Entries[0].anchors[0].label)
        assertEquals(1400, u2Entries[0].anchors[1].scrollY)
    }

    @Test fun lastGroupTabId_matches_id_committed_to_saved_tabs() = runTest {
        // Regression: the post-save "Open" banner routes via
        // coordinator.lastGroupTabId. If that doesn't match the saved_tabs.id
        // the sink wrote, PageGroupViewerScreen.bind() can't find the parent
        // row and the viewer renders empty. Sidecar lookups break for the
        // same reason (keyed on groupTabId).
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = FakePageGroupDriver(succeed = setOf("u1"))
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "Title",
            toc = listOf(TocEntry("Index", "u1", null))
        ) { driver }
        advanceUntilIdle()

        assertNotNull("commitGroup should have run on the happy path", sink.committedGroupTabId)
        assertEquals(
            "coordinator.lastGroupTabId must equal the id the sink committed to saved_tabs",
            sink.committedGroupTabId, c.lastGroupTabId
        )
    }

    @Test fun per_url_failure_yields_empty_anchors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = FakePageGroupDriver(
            succeed = setOf("u1", "u3"),
            fail = setOf("u2"),
            anchorsByUrl = mapOf("u1" to listOf(RawAnchor("Top", 0)))
        )
        val sink = FakeGroupRepoSink()
        val c = newCoordinator(dispatcher, sink)

        c.start(
            vaultId = "v1", indexUrl = "u1", indexPageTitle = "X",
            toc = listOf(
                TocEntry("Index", "u1", null),
                TocEntry("C1", "u2", null),
                TocEntry("C2", "u3", null)
            )
        ) { driver }
        advanceUntilIdle()

        val entries = sink.created!!.entries
        assertEquals(1, entries.first { it.savedHref == "u1" }.anchors.size)
        assertEquals(0, entries.first { it.savedHref == "u2" }.anchors.size)
        assertEquals(0, entries.first { it.savedHref == "u3" }.anchors.size)
    }
}

/**
 * Fake driver: simulate a WebView. Loads succeed when the URL is in
 * [succeed]; "fail" URLs return false from loadAndAwait. "hangOn" URLs
 * never complete (used to test cancellation).
 */
private class FakePageGroupDriver(
    val succeed: Set<String> = emptySet(),
    val fail: Set<String> = emptySet(),
    val hangOn: String? = null,
    val anchorsByUrl: Map<String, List<RawAnchor>> = emptyMap()
) : PageGroupSaveDriver {
    var lastLoadedUrl: String? = null
        private set
    override suspend fun loadAndAwait(url: String): Boolean {
        lastLoadedUrl = url
        if (url == hangOn) kotlinx.coroutines.awaitCancellation()
        return url in succeed && url !in fail
    }
    override suspend fun saveWebArchive(absolutePath: String): Boolean {
        File(absolutePath).writeText("fake-mht")
        return true
    }
    override suspend fun mainHtml(): String? = "<html><body><h1>X</h1><h2 id='a'>A</h2></body></html>"
    override suspend fun extractAnchors(): List<RawAnchor> =
        anchorsByUrl[lastLoadedUrl].orEmpty()
    override fun destroy() {}
}

/** Test sink that records what the coordinator handed off to the repository. */
private class FakeGroupRepoSink(
    /** URLs for which [stageSavedPage] should return null (simulates storage failure). */
    val failStageFor: Set<String> = emptySet()
) : PageGroupSaveCoordinator.GroupSaveSink {
    var created: CreatedGroup? = null
    var rolledBackFiles: Boolean = false
    /**
     * The id of the saved_tabs row this sink committed. After the fix the
     * sink uses the [groupTabId] handed to [commitGroup]; the coordinator
     * exposes the same value as `lastGroupTabId`.
     */
    var committedGroupTabId: String? = null
    private var nextRid = 1

    override suspend fun stageSavedPage(
        groupTabId: String, vaultId: String, groupTitle: String,
        savedHref: String, tempMht: File, pageIndex: Int
    ): String? = if (savedHref in failStageFor) null else "r${nextRid++}"

    override suspend fun commitGroup(
        groupTabId: String,
        vaultId: String, title: String, indexUrl: String, indexPageTitle: String,
        entries: List<PageGroupSaveCoordinator.PreparedEntry>
    ) {
        committedGroupTabId = groupTabId
        created = CreatedGroup(vaultId, title, indexUrl, indexPageTitle, entries)
    }
    override suspend fun rollbackGroupArtifacts(groupTabId: String) { rolledBackFiles = true }

    data class CreatedGroup(
        val vaultId: String, val title: String, val indexUrl: String, val indexPageTitle: String,
        val entries: List<PageGroupSaveCoordinator.PreparedEntry>
    )
}
