package com.twinlore.app.data.library

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.local.MediaSearchPathEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.MediaSearchPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaIndexerScanLifecycleTest {

    @get:Rule val tempFolder = TemporaryFolder()
    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var indexer: MediaIndexer
    private lateinit var appScope: CoroutineScope
    private val sanitizer = GameNameSanitizer()

    /**
     * Subscribe to [flow] on [appScope] and accumulate emissions into the
     * returned list. Suspends until the collector is actually registered
     * (via `onSubscription`) so callers can safely trigger producers
     * afterwards — MediaIndexer.events is `SharedFlow(replay = 0)`, so any
     * event emitted before the collector subscribes is silently dropped
     * (the original cause of the Started-event flake).
     *
     * The wait runs on real time (System.currentTimeMillis + Dispatchers.
     * Default delays) because the collector lives on appScope's real
     * Dispatchers.IO; runTest's virtual scheduler can't observe it.
     */
    private suspend fun <T : Any> collectAsync(flow: SharedFlow<T>): Pair<MutableList<T>, Job> {
        val collected = mutableListOf<T>()
        val subscribed = CompletableDeferred<Unit>()
        val job = appScope.launch {
            flow.onSubscription { subscribed.complete(Unit) }
                .collect { collected += it }
        }
        val deadline = System.currentTimeMillis() + 2_000
        while (!subscribed.isCompleted) {
            check(System.currentTimeMillis() < deadline) {
                "Collector never subscribed within 2s"
            }
            withContext(Dispatchers.Default) { delay(5) }
        }
        return collected to job
    }

    /**
     * Poll [collected] on real time until an element of type [T] arrives or
     * the deadline expires. Real time is required because the producer runs
     * on [appScope] (real Dispatchers.IO) — runTest's virtual scheduler
     * cannot advance it.
     */
    private suspend inline fun <reified T : MediaIndexer.ScanEvent> awaitEvent(
        collected: List<MediaIndexer.ScanEvent>,
        timeoutMs: Long = 5_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (collected.none { it is T }) {
            check(System.currentTimeMillis() < deadline) {
                "Timed out waiting for ${T::class.simpleName}; observed: $collected"
            }
            withContext(Dispatchers.Default) { delay(20) }
        }
    }

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java)
            .allowMainThreadQueries().build()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        indexer = MediaIndexer(
            context = ctx,
            pathDao = db.mediaSearchPathDao(),
            indexDao = db.mediaSearchIndexDao(),
            dirDao = db.mediaSearchDirDao(),
            sanitizer = sanitizer,
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    private suspend fun seedPath(disabled: Boolean = false): String {
        val root = tempFolder.newFolder("root-${System.nanoTime()}")
        java.io.File(root, "Resident Evil 4").mkdirs()
        java.io.File(root, "Resident Evil 4/cover.png").writeText("fake")
        val sp = MediaSearchPathEntity(
            id = "p-${System.nanoTime()}",
            uri = "file://${root.absolutePath}",
            label = "Test path",
            origin = MediaSearchPath.Origin.MANUAL.raw,
            lastScannedAt = null,
            createdAt = 0,
            disabled = disabled,
        )
        db.mediaSearchPathDao().upsert(sp)
        return sp.id
    }

    @Test
    fun startScanForPath_emitsStartedThenFinishedPerPath() = runTest {
        val spId = seedPath()
        val (collected, collectorJob) = collectAsync(indexer.events)
        indexer.startScanForPath(spId)
        awaitEvent<MediaIndexer.ScanEvent.Finished.PerPath>(collected)
        collectorJob.cancel()
        // Finished.PerPath is emitted strictly after Started in production
        // (MediaIndexer.startScanForPath), so observing Finished guarantees
        // Started arrived first.
        assertTrue("Started fired", collected.any { it is MediaIndexer.ScanEvent.Started })
        assertTrue("Finished.PerPath fired", collected.any { it is MediaIndexer.ScanEvent.Finished.PerPath })
    }

    @Test
    fun startScanAll_emitsFinishedGlobalOnly() = runTest {
        seedPath()
        val (collected, collectorJob) = collectAsync(indexer.events)
        indexer.startScanAll()
        awaitEvent<MediaIndexer.ScanEvent.Finished.Global>(collected)
        collectorJob.cancel()
        assertTrue("Finished.Global fired", collected.any { it is MediaIndexer.ScanEvent.Finished.Global })
        assertTrue("No Started events for global", collected.none { it is MediaIndexer.ScanEvent.Started })
    }

    @Test
    fun startScanForPath_skipsDisabledPath() = runTest {
        val disabledId = seedPath(disabled = true)
        val (collected, collectorJob) = collectAsync(indexer.events)
        indexer.startScanForPath(disabledId)
        // The disabled branch in MediaIndexer.startScanForPath is one DAO
        // read + an early `return@launch` — finishes within microseconds on
        // appScope. There is no signal to wait on (no events, no state
        // change), so the safest condition we can poll is "the index DAO
        // and event list stay empty long enough that any belated work
        // would have surfaced". 200ms of stable-emptiness with a 2s cap.
        val deadline = System.currentTimeMillis() + 2_000
        val stableMs = 200L
        var stableSince = System.currentTimeMillis()
        while (System.currentTimeMillis() - stableSince < stableMs) {
            check(System.currentTimeMillis() < deadline) {
                "Disabled-path test never stabilized; observed: $collected"
            }
            withContext(Dispatchers.Default) { delay(20) }
            if (collected.isNotEmpty() || db.mediaSearchIndexDao().count() > 0) {
                // Something fired — reset the stable window so the assertion
                // below trips with a meaningful message rather than passing
                // by accident before the next poll.
                stableSince = System.currentTimeMillis()
            }
        }
        collectorJob.cancel()
        assertEquals(0, collected.size)
        assertEquals(0, db.mediaSearchIndexDao().count())
    }
}
