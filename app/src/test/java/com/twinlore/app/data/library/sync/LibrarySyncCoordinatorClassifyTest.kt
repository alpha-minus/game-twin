package com.twinlore.app.data.library.sync

import android.net.Uri
import com.twinlore.app.data.library.LibrarySnapshotMeta
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySyncCoordinatorClassifyTest {

    private fun meta(deviceId: String, at: Long, schema: Int = 35) = LibrarySnapshotMeta(
        uri = Uri.parse("file:///tmp/library.x.gametwin"),
        fileName = "library.x.gametwin",
        deviceId = deviceId, deviceLabel = "Dev-$deviceId",
        lastWrittenAtMillis = at, schemaVersion = schema,
    )

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `classify - merged snapshots are not changed candidates`() {
        val now = 1_000L * day
        val r = LibrarySyncCoordinator.classify(
            metas = listOf(
                meta("me", at = now),                  // own -> ignored entirely
                meta("d1", at = now - 91 * day),       // merged + dormant -> NOT changed (and never deleted)
                meta("d2", at = now - 91 * day),       // NOT merged but STALE (orphan install) -> never merged
                meta("d3", at = now - 1 * day),        // merged + recent -> not changed
                meta("d4", at = now, schema = 99),     // too new -> notice, not changed
                meta("d5", at = now - 20 * day),       // NOT merged + fresh enough -> changed candidate
            ),
            ownDeviceId = "me",
            markers = mapOf("d1" to now - 91 * day, "d3" to now - 1 * day),
            supportedSchemaVersion = 35,
            nowMillis = now,
        )
        assertEquals(listOf("d5"), r.changed.map { it.deviceId })
        assertEquals(listOf("d4"), r.tooNew.map { it.deviceId })
        assertEquals(setOf("d1", "d2", "d3", "d4", "d5"), r.foreign.map { it.deviceId }.toSet())
    }

    @Test
    fun `classify - stale orphan snapshots are never merge candidates even on fresh installs`() {
        // A fresh install has NO merge markers. Pre-ANDROID_ID orphan
        // snapshots (dead installs) used to merge once here, resurrecting
        // months-old vault states that then deleted live files via the
        // wholesale per-vault merge (the Castlevania .stversions incident).
        val now = 1_000L * day
        val r = LibrarySyncCoordinator.classify(
            metas = listOf(
                meta("orphan1", at = now - 45 * day),
                meta("orphan2", at = now - 365 * day),
                meta("live", at = now - 2 * day),
            ),
            ownDeviceId = "me",
            markers = emptyMap(), // fresh install
            supportedSchemaVersion = 35,
            nowMillis = now,
        )
        assertEquals(listOf("live"), r.changed.map { it.deviceId })
    }

    @Test
    fun `change-set key is stable and order-independent`() {
        val a = LibrarySyncCoordinator.changeSetKey(listOf(meta("b", 2), meta("a", 1)))
        val b = LibrarySyncCoordinator.changeSetKey(listOf(meta("a", 1), meta("b", 2)))
        assertEquals(a, b)
    }

    @Test
    fun `blank deviceId snapshots are ignored`() {
        val r = LibrarySyncCoordinator.classify(
            metas = listOf(meta("", at = 100L)),
            ownDeviceId = "me", markers = emptyMap(),
            supportedSchemaVersion = 35,
            nowMillis = 100L,
        )
        assertEquals(0, r.foreign.size)
    }
}
