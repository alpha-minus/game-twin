package com.twinlore.app.data.library

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric is used because Uri.parse is part of the Android framework and
 * the resolver builds URIs from the home-folder URI plus a relative path.
 */
@RunWith(RobolectricTestRunner::class)
class ResourceResolverTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val dao: ResourceFileDao = mock()
    private val vaultDao: VaultDao = mock()
    private val folder: LibraryFolderManager = mock()
    private val resolver = ResourceResolver(
        RuntimeEnvironment.getApplication(),
        dao,
        vaultDao,
        folder,
        clock = { 1_000L },
        // Robolectric's Environment.isExternalStorageManager() shadow throws once
        // android resources are included (needed for Compose tests elsewhere in the
        // suite); these tests exercise the legacy/SAF path, which is what the real
        // default resolved to before that flag was on, so pin it explicitly rather
        // than relying on the (now-throwing) production default.
        isAllFilesAccessGranted = { false }
    )

    private fun grantedResolver() = ResourceResolver(
        RuntimeEnvironment.getApplication(),
        dao,
        vaultDao,
        folder,
        clock = { 1_000L },
        isAllFilesAccessGranted = { true }
    )

    private fun ungrantedResolver() = ResourceResolver(
        RuntimeEnvironment.getApplication(),
        dao,
        vaultDao,
        folder,
        clock = { 1_000L },
        isAllFilesAccessGranted = { false }
    )

    @Test
    fun resolve_returns_cached_uri_and_touches_lastSeen_when_path_still_exists() = runTest {
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        val uri = resolver.resolve("r1")

        assertEquals(Uri.parse("file:///tmp/home/Zelda/guide.mht"), uri)
        verify(dao).touchLastSeen("r1", 1_000L)
        verify(dao, never()).update(any())
    }

    @Test
    fun resolve_recovers_via_findByNameAndSizeInVault_when_cached_path_missing() = runTest {
        val row = stub(homeRelativePath = "Old/guide.mht", filename = "guide.mht", sizeBytes = 999L)
        whenever(dao.getById("r2")).thenReturn(row)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vaultEntity("v1", "Zelda"))
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)
        whenever(folder.findByNameAndSizeInVault("Zelda", "guide.mht", 999L)).thenReturn(
            FoundFile(Uri.parse("file:///tmp/home/Zelda/guide.mht"), "/tmp/home/Zelda/guide.mht", "Zelda/guide.mht")
        )

        val uri = resolver.resolve("r2")

        assertEquals(Uri.parse("file:///tmp/home/Zelda/guide.mht"), uri)
        verify(dao).update(any())
    }

    @Test
    fun resolve_returns_null_when_recovery_fails() = runTest {
        val row = stub(homeRelativePath = "Old/x.png", filename = "x.png", sizeBytes = 10L)
        whenever(dao.getById("r3")).thenReturn(row)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vaultEntity("v1", "Mario"))
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)
        whenever(folder.findByNameAndSizeInVault("Mario", "x.png", 10L)).thenReturn(null)

        assertNull(resolver.resolve("r3"))
    }

    @Test
    fun resolve_returns_null_when_vault_missing_during_recovery() = runTest {
        val row = stub(homeRelativePath = "Old/x.png", filename = "x.png", sizeBytes = 10L)
        whenever(dao.getById("r5")).thenReturn(row)
        whenever(vaultDao.getVaultById("v1")).thenReturn(null)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)

        assertNull(resolver.resolve("r5"))
    }

    @Test
    fun resolve_returns_null_for_unknown_id() = runTest {
        whenever(dao.getById("missing")).thenReturn(null)

        assertNull(resolver.resolve("missing"))
    }

    @Test
    fun resolve_uses_absolute_path_when_homeRelativePath_is_null() = runTest {
        val row = stub(homeRelativePath = null, absolutePath = "/sdcard/Downloads/extern.pdf")
        whenever(dao.getById("r4")).thenReturn(row)
        whenever(folder.exists(any())).thenReturn(true)

        val uri = resolver.resolve("r4")

        assertEquals(Uri.parse("file:///sdcard/Downloads/extern.pdf"), uri)
    }

    @Test
    fun resolve_caches_successful_resolve_so_second_call_touches_no_dao_or_folder_io() = runTest {
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        val first = resolver.resolve("r1")
        val second = resolver.resolve("r1")

        assertEquals(first, second)
        // Cache hit must do zero IO: no second row lookup, no second existence
        // check, and no second touchLastSeen write.
        verify(dao, times(1)).getById("r1")
        verify(folder, times(1)).exists(any())
        verify(dao, times(1)).touchLastSeen(any(), any())
    }

    @Test
    fun resolve_does_not_cache_a_failed_resolve_so_a_later_call_retries() = runTest {
        val row = stub(homeRelativePath = "Old/x.png", filename = "x.png", sizeBytes = 10L)
        whenever(dao.getById("r3")).thenReturn(row)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vaultEntity("v1", "Mario"))
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)
        whenever(folder.findByNameAndSizeInVault("Mario", "x.png", 10L)).thenReturn(null)

        assertNull(resolver.resolve("r3"))
        assertNull(resolver.resolve("r3"))

        // Neither call was a cache hit: dao.getById ran for both.
        verify(dao, times(2)).getById("r3")
    }

    @Test
    fun resolve_does_not_cache_when_a_collaborator_throws_partway_through_recovery() = runTest {
        // Recovery finds the file (so a Uri is in hand) but the row-rewrite
        // write throws before the cache is populated — this is the "negative
        // caching, including the case where an exception is thrown partway
        // through" risk named in the brief.
        val row = stub(homeRelativePath = "Old/x.png", filename = "x.png", sizeBytes = 10L)
        whenever(dao.getById("r6")).thenReturn(row)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vaultEntity("v1", "Mario"))
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)
        whenever(folder.findByNameAndSizeInVault("Mario", "x.png", 10L)).thenReturn(
            FoundFile(Uri.parse("file:///tmp/home/Mario/x.png"), "/tmp/home/Mario/x.png", "Mario/x.png")
        )
        whenever(dao.update(any())).thenThrow(RuntimeException("boom"))

        try {
            resolver.resolve("r6")
            org.junit.Assert.fail("expected the dao.update failure to propagate")
        } catch (expected: RuntimeException) {
            assertEquals("boom", expected.message)
        }

        // The blown-up attempt must not have poisoned the cache with a partial
        // entry. Once the collaborator stops throwing, a later resolve does its
        // own fresh IO (a second dao.getById) and succeeds normally, rather than
        // being served a leftover/half-written cache entry from the failure.
        whenever(dao.update(any())).then { }
        val uri = resolver.resolve("r6")

        assertEquals(Uri.parse("file:///tmp/home/Mario/x.png"), uri)
        verify(dao, times(2)).getById("r6")
    }

    @Test
    fun resolve_serves_from_cache_before_ttl_expiry_and_re_resolves_after() = runTest {
        // The default `resolver` field always reads the real elapsedRealtime, which is
        // unsuitable for asserting TTL boundaries, so this test builds its own instance
        // with a fake, fully-controllable elapsed clock.
        var elapsedMs = 0L
        val ttlResolver = ResourceResolver(
            RuntimeEnvironment.getApplication(),
            dao,
            vaultDao,
            folder,
            clock = { 1_000L },
            elapsedRealtime = { elapsedMs },
            isAllFilesAccessGranted = { false }
        )
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        val first = ttlResolver.resolve("r1")

        // Just under the TTL: still served from cache, zero extra IO.
        elapsedMs = 44_999L
        val second = ttlResolver.resolve("r1")
        assertEquals(first, second)
        verify(dao, times(1)).getById("r1")

        // At/after the TTL: the entry is treated as a miss and re-resolved (existence
        // re-checked), which is what lets the class heal from an external delete/rename.
        elapsedMs = 45_000L
        val third = ttlResolver.resolve("r1")
        assertEquals(first, third)
        verify(dao, times(2)).getById("r1")
        verify(folder, times(2)).exists(any())
    }

    @Test
    fun invalidate_drops_a_single_cached_entry_forcing_a_fresh_resolve() = runTest {
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        resolver.resolve("r1")
        resolver.invalidate("r1")
        resolver.resolve("r1")

        verify(dao, times(2)).getById("r1")
    }

    @Test
    fun invalidateAll_drops_every_cached_entry() = runTest {
        val row1 = stub(id = "a1", homeRelativePath = "Zelda/guide.mht")
        val row2 = stub(id = "a2", homeRelativePath = "Mario/guide.mht")
        whenever(dao.getById("a1")).thenReturn(row1)
        whenever(dao.getById("a2")).thenReturn(row2)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        resolver.resolve("a1")
        resolver.resolve("a2")
        resolver.invalidateAll()
        resolver.resolve("a1")
        resolver.resolve("a2")

        verify(dao, times(2)).getById("a1")
        verify(dao, times(2)).getById("a2")
    }

    // --- Fast path: filesystem resolution when All-Files access is granted (round two, task 1) ---

    @Test
    fun resolve_uses_direct_file_path_when_access_granted_and_root_derives() = runTest {
        val vaultDir = tempFolder.newFolder("Zelda")
        val file = File(vaultDir, "guide.mht").apply { writeText("data") }
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)

        val uri = grantedResolver().resolve("r1")

        assertEquals(Uri.fromFile(file), uri)
        verify(dao).touchLastSeen("r1", 1_000L)
        // Fast-path hit must never touch the SAF/legacy machinery.
        verify(folder, never()).homeFolderUri()
        verify(folder, never()).exists(any())
    }

    @Test
    fun resolve_falls_back_to_legacy_path_when_root_does_not_derive() = runTest {
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(null)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        val uri = grantedResolver().resolve("r1")

        assertEquals(Uri.parse("file:///tmp/home/Zelda/guide.mht"), uri)
        verify(folder).exists(any())
    }

    @Test
    fun resolve_falls_back_to_legacy_path_when_access_not_granted_even_if_root_would_derive() = runTest {
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        // Deliberately stubbed to succeed, to prove the gate is checked BEFORE
        // deriving the root, not just before trusting a null result.
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(true)

        val uri = ungrantedResolver().resolve("r1")

        assertEquals(Uri.parse("file:///tmp/home/Zelda/guide.mht"), uri)
        verify(folder, never()).homeFolderFsRoot()
    }

    @Test
    fun resolve_fast_path_miss_falls_through_to_recovery() = runTest {
        // The derived root exists, but the file at the stored relative path is gone
        // (renamed/deleted outside the app) -- this must not surface as "resolved to
        // nothing"; it must fall through to the same filename+size recovery the
        // legacy path already had.
        val row = stub(homeRelativePath = "Old/guide.mht", filename = "guide.mht", sizeBytes = 999L)
        whenever(dao.getById("r2")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        whenever(vaultDao.getVaultById("v1")).thenReturn(vaultEntity("v1", "Zelda"))
        whenever(folder.homeFolderUri()).thenReturn(Uri.parse("file:///tmp/home"))
        whenever(folder.exists(any())).thenReturn(false)
        whenever(folder.findByNameAndSizeInVault("Zelda", "guide.mht", 999L)).thenReturn(
            FoundFile(Uri.parse("file:///tmp/home/Zelda/guide.mht"), "/tmp/home/Zelda/guide.mht", "Zelda/guide.mht")
        )

        val uri = grantedResolver().resolve("r2")

        assertEquals(Uri.parse("file:///tmp/home/Zelda/guide.mht"), uri)
        verify(dao).update(any())
    }

    @Test
    fun resolve_fast_path_rechecks_existence_every_call_instead_of_a_ttl() = runTest {
        val vaultDir = tempFolder.newFolder("Zelda")
        val file = File(vaultDir, "guide.mht").apply { writeText("data") }
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        val fast = grantedResolver()

        fast.resolve("r1")
        fast.resolve("r1")

        // No Uri-level TTL cache on the fast path: every resolve re-fetches the row
        // and re-checks existence, which is what makes it self-healing.
        verify(dao, times(2)).getById("r1")

        // Delete the file underneath the resolver; the very next call must notice
        // immediately, not up to 45s later.
        file.delete()
        assertNull(fast.resolve("r1"))
    }

    @Test
    fun resolve_fast_path_throttles_touchLastSeen_but_still_rechecks_existence_every_call() = runTest {
        // dao.touchLastSeen is a Room UPDATE that also fires the invalidation tracker for
        // any observer of resource_files -- doing it on every single resolve (the fast path
        // has no Uri-level cache to naturally space these out, unlike the legacy path's TTL
        // cache) would reintroduce exactly the "avoidable IO on every call" cost the TTL
        // cache existed to prevent. The existence check itself must stay per-call (that's
        // what makes self-healing instant); only this bookkeeping write is throttled.
        val vaultDir = tempFolder.newFolder("Zelda")
        File(vaultDir, "guide.mht").writeText("data")
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        var elapsedMs = 0L
        val fast = ResourceResolver(
            RuntimeEnvironment.getApplication(),
            dao,
            vaultDao,
            folder,
            clock = { 1_000L },
            elapsedRealtime = { elapsedMs },
            isAllFilesAccessGranted = { true }
        )

        fast.resolve("r1")
        elapsedMs = 1_000L
        fast.resolve("r1")

        // Throttled: only the first call within the window wrote lastSeenAt...
        verify(dao, times(1)).touchLastSeen(any(), any())
        // ...but the row lookup (and therefore the existence check) ran both times.
        verify(dao, times(2)).getById("r1")

        elapsedMs = 46_000L
        fast.resolve("r1")

        verify(dao, times(2)).touchLastSeen(any(), any())
    }

    @Test
    fun resolve_fast_path_throttles_touchLastSeen_exactly_once_under_concurrent_callers() = runBlocking {
        // Regression test: maybeTouchLastSeen used to be a non-atomic
        // read-compare-suspend-write. The same artwork can legitimately be resolved at
        // once from a grid tile, a carousel background and a showcase blur, so several
        // real coroutines racing the same id must still produce exactly one
        // dao.touchLastSeen write, not one per racer. This needs genuine thread-level
        // concurrency (not runTest's single-threaded virtual-time scheduler) to exercise
        // the race at all, so it uses runBlocking + Dispatchers.Default and a rendezvous
        // to line every caller up at the same starting line.
        //
        // The rendezvous is built from suspending primitives only (AtomicInteger +
        // yield, CompletableDeferred.await) rather than a java.util.concurrent
        // CountDownLatch: a *blocking* await from inside a Dispatchers.Default
        // coroutine parks the real OS thread it's running on, and with `concurrency`
        // greater than that dispatcher's (CPU-core-sized) thread pool, every thread
        // ends up parked waiting for a countdown that the not-yet-started remaining
        // coroutines can never reach -- a thread-pool-exhaustion deadlock, not a race.
        val vaultDir = tempFolder.newFolder("Zelda")
        File(vaultDir, "guide.mht").writeText("data")
        val row = stub(homeRelativePath = "Zelda/guide.mht")
        whenever(dao.getById("r1")).thenReturn(row)
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        val fast = grantedResolver()

        val concurrency = 32
        val ready = AtomicInteger(0)
        val go = CompletableDeferred<Unit>()
        coroutineScope {
            repeat(concurrency) {
                launch(Dispatchers.Default) {
                    ready.incrementAndGet()
                    go.await()
                    fast.resolve("r1")
                }
            }
            while (ready.get() < concurrency) yield()
            go.complete(Unit)
        }

        verify(dao, times(1)).touchLastSeen(any(), any())
    }

    @Test
    fun resolve_caches_the_derived_fs_root_across_calls() = runTest {
        val vaultDir = tempFolder.newFolder("Zelda")
        File(vaultDir, "a.png").writeText("a")
        File(vaultDir, "b.png").writeText("b")
        whenever(dao.getById("r1")).thenReturn(stub(id = "r1", homeRelativePath = "Zelda/a.png"))
        whenever(dao.getById("r2")).thenReturn(stub(id = "r2", homeRelativePath = "Zelda/b.png"))
        whenever(folder.homeFolderFsRoot()).thenReturn(tempFolder.root)
        val fast = grantedResolver()

        fast.resolve("r1")
        fast.resolve("r2")

        // The root is derived once and reused, not re-derived for every resource id.
        verify(folder, times(1)).homeFolderFsRoot()
    }

    // --- SAF directory memoization (the rescan-is-slow fix) ---

    @Test
    fun content_home_walk_looks_each_directory_up_once_across_rows() = runTest {
        // A rescan resolves hundreds of rows that share the same few directories.
        // Walking from the tree root per row re-listed every intermediate directory,
        // which is the bulk of the SAF traffic an integrity check generates. Only
        // the leaf is per-row; the directories above it are memoized.
        val home = Uri.parse("content://com.example.docs/tree/primary%3AHome")
        whenever(folder.homeFolderUri()).thenReturn(home)
        whenever(folder.exists(any())).thenReturn(true)

        val lookups = mutableListOf<String>()
        val vaultDir: DocumentFile = mock()
        val pagesDir: DocumentFile = mock()
        val memoResolver = ResourceResolver(
            RuntimeEnvironment.getApplication(),
            dao,
            vaultDao,
            folder,
            clock = { 1_000L },
            isAllFilesAccessGranted = { false },
            findChild = { _, name ->
                lookups += name
                when (name) {
                    "Zelda" -> vaultDir
                    "pages" -> pagesDir
                    else -> mock<DocumentFile>().also {
                        whenever(it.uri).thenReturn(Uri.parse("content://com.example.docs/doc/$name"))
                    }
                }
            }
        )
        whenever(dao.getById("p1"))
            .thenReturn(stub(id = "p1", homeRelativePath = "Zelda/pages/page-1.mht"))
        whenever(dao.getById("p2"))
            .thenReturn(stub(id = "p2", homeRelativePath = "Zelda/pages/page-2.mht"))

        memoResolver.resolve("p1")
        memoResolver.resolve("p2")

        assertEquals(listOf("Zelda", "pages", "page-1.mht", "page-2.mht"), lookups)
    }

    @Test
    fun invalidateAll_drops_memoized_directory_handles() = runTest {
        // invalidateAll is what LibraryIntegrityChecker calls to force a cold,
        // trustworthy audit, so the directory memo must not survive it either.
        val home = Uri.parse("content://com.example.docs/tree/primary%3AHome")
        whenever(folder.homeFolderUri()).thenReturn(home)
        whenever(folder.exists(any())).thenReturn(true)

        val lookups = mutableListOf<String>()
        val vaultDir: DocumentFile = mock()
        val memoResolver = ResourceResolver(
            RuntimeEnvironment.getApplication(),
            dao,
            vaultDao,
            folder,
            clock = { 1_000L },
            isAllFilesAccessGranted = { false },
            findChild = { _, name ->
                lookups += name
                if (name == "Zelda") vaultDir else mock<DocumentFile>().also {
                    whenever(it.uri).thenReturn(Uri.parse("content://com.example.docs/doc/$name"))
                }
            }
        )
        whenever(dao.getById("p1"))
            .thenReturn(stub(id = "p1", homeRelativePath = "Zelda/guide.mht"))

        memoResolver.resolve("p1")
        memoResolver.invalidateAll()
        memoResolver.resolve("p1")

        assertEquals(listOf("Zelda", "guide.mht", "Zelda", "guide.mht"), lookups)
    }

    @Test
    fun fast_path_refuses_a_relative_path_that_climbs_out_of_home() = runTest {
        val home = tempFolder.newFolder("home")
        val escaped = File(tempFolder.root, "escape.txt").apply { writeText("x") }
        whenever(folder.homeFolderFsRoot()).thenReturn(home)
        whenever(folder.homeFolderUri()).thenReturn(null)
        whenever(dao.getById("r1")).thenReturn(stub(homeRelativePath = "../escape.txt"))

        assertNull(grantedResolver().resolve("r1"))
        assertTrue(escaped.exists())
    }

    @Test
    fun isPlainRelativePath_accepts_nested_and_rejects_climbing_or_absolute() {
        assertTrue(ResourceResolver.isPlainRelativePath("Zelda/guide.mht"))
        assertTrue(ResourceResolver.isPlainRelativePath("a/b/c..d.png"))
        assertFalse(ResourceResolver.isPlainRelativePath("../x"))
        assertFalse(ResourceResolver.isPlainRelativePath("a/../../x"))
        assertFalse(ResourceResolver.isPlainRelativePath("/abs"))
        assertFalse(ResourceResolver.isPlainRelativePath(""))
    }

    private fun stub(
        id: String = "r1",
        filename: String = "guide.mht",
        sizeBytes: Long = 100L,
        homeRelativePath: String? = null,
        absolutePath: String? = null
    ) = ResourceFileEntity(
        id = id,
        vaultId = "v1",
        kind = "guide",
        filename = filename,
        sizeBytes = sizeBytes,
        homeRelativePath = homeRelativePath,
        absolutePath = absolutePath,
        mimeType = null,
        source = "user_add",
        lastSeenAt = 0L,
        createdAt = 0L
    )

    private fun vaultEntity(id: String, name: String) = VaultEntity(
        id = id,
        name = name,
        romPath = null,
        romFilename = null,
        artworkVibrantArgb = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = null,
        steamAppId = null,
        lastOpened = null,
        createdAt = Date(0L)
    )
}
