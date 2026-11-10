package com.twinlore.app.ui.screens.group

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.GroupsFolderResolver
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.VaultDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression coverage for cross-document anchor link navigation in the
 * page-group viewer: tapping `page2.html#section` must carry the `#section`
 * fragment to the destination so it lands on the anchor, NOT on the page's
 * saved resume scroll position. The fragment rides through [onEntryTapped]
 * into a single-shot queue the screen's cross-file load path drains with
 * precedence over the saved scroll_ratio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageGroupViewerViewModelTappedFragmentTest {
    private lateinit var db: VaultDatabase
    private lateinit var groupRoot: File
    private lateinit var vm: PageGroupViewerViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        groupRoot = File.createTempFile("group-root", "").apply { delete(); mkdirs() }
        val repo = PageGroupRepository(
            savedTabDao = db.savedTabDao(),
            resourceFileDao = db.resourceFileDao(),
            pageGroupEntryDao = db.pageGroupEntryDao(),
            pageGroupEntryAnchorDao = db.pageGroupEntryAnchorDao(),
            scrollMarkerDao = db.scrollMarkerDao(),
            groupsFolderResolver = object : GroupsFolderResolver {
                override suspend fun resolve(groupTabId: String): File = groupRoot
            },
            database = db
        )
        vm = PageGroupViewerViewModel(
            savedTabDao = db.savedTabDao(),
            pageGroupEntryDao = db.pageGroupEntryDao(),
            repository = repo
        )
    }

    @After fun tearDown() { db.close(); groupRoot.deleteRecursively() }

    @Test fun anchorTap_queuesFragment_singleShot() {
        vm.onEntryTapped("entry-2", "section-5")
        assertEquals("section-5", vm.consumePendingTappedFragment())
        assertNull("queue must be empty after consume", vm.consumePendingTappedFragment())
    }

    @Test fun plainTap_clearsStaleFragment() {
        vm.onEntryTapped("entry-2", "section-5")
        // A subsequent non-anchored tap (Prev/Next, ToC, plain link) must not
        // inherit the previous tap's fragment.
        vm.onEntryTapped("entry-3")
        assertNull(vm.consumePendingTappedFragment())
    }

    @Test fun blankFragment_treatedAsNull() {
        vm.onEntryTapped("entry-2", "")
        assertNull(vm.consumePendingTappedFragment())
    }

    @Test fun clearPendingTappedFragment_dropsQueued() {
        vm.onEntryTapped("entry-2", "frag")
        vm.clearPendingTappedFragment()
        assertNull(vm.consumePendingTappedFragment())
    }
}
