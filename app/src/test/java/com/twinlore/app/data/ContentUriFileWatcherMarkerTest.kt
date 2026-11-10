package com.twinlore.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * readLaunchMarker's null contract: every "nothing to watch" shape must come back
 * as null, never as a sentinel string, because MainViewModel compares markers for
 * equality to decide whether a new launch happened. (The positive path needs a
 * real SAF documents provider and is covered by device verification instead.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContentUriFileWatcherMarkerTest {

    private val watcher = ContentUriFileWatcher(RuntimeEnvironment.getApplication())

    @Test
    fun null_and_blank_uris_yield_no_marker() = runTest {
        assertNull(watcher.readLaunchMarker(null, LastPlayedSource.ES_DE))
        assertNull(watcher.readLaunchMarker("", LastPlayedSource.ES_DE))
        assertNull(watcher.readLaunchMarker("   ", LastPlayedSource.COCOON))
    }

    @Test
    fun fileless_sources_yield_no_marker() = runTest {
        assertNull(watcher.readLaunchMarker("content://any", LastPlayedSource.ACHIEVEMENTS))
        assertNull(watcher.readLaunchMarker("content://any", LastPlayedSource.NOT_CONFIGURED))
    }

    @Test
    fun unresolvable_tree_uri_yields_no_marker_instead_of_throwing() = runTest {
        // Not a tree URI; DocumentFile.fromTreeUri throws or returns an unusable
        // document. Either way the caller must see null.
        assertNull(watcher.readLaunchMarker("content://com.nonexistent/thing", LastPlayedSource.ES_DE))
        assertNull(watcher.readLaunchMarker("content://com.nonexistent/thing", LastPlayedSource.RETROARCH))
    }
}
