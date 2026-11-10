package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DORMANT by design: session recording was built and then deliberately switched off
 * so ES-DE's own files are the only source of play statistics, with
 * no app-recorded metadata. Its event-log listener (formerly ES-DE Live Mode) was later
 * removed entirely. Nothing writes or reads this table today. It stays declared
 * (schema v36) so existing installs don't need a destructive migration; if recording
 * is ever wanted again, a new listener will need to persist here on game end.
 */
@Entity(
    tableName = "play_sessions",
    indices = [Index("startedAtEpochSeconds"), Index("system", "romPath")],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ES-DE short system name (e.g. "snes"), lowercase as delivered by the event log. */
    val system: String,
    /** Rom path as reported by the game-start event (absolute or gamelist-relative). */
    val romPath: String,
    /** Display name from the event, so stats survive a game vanishing from gamelists. */
    val gameName: String,
    val startedAtEpochSeconds: Long,
    val endedAtEpochSeconds: Long,
    val durationSeconds: Long,
)

@Dao
interface PlaySessionDao {
    @Insert
    suspend fun insert(session: PlaySessionEntity)

    /** All sessions, oldest first — the stats screen aggregates in memory (handheld-scale data). */
    @Query("SELECT * FROM play_sessions ORDER BY startedAtEpochSeconds")
    fun allFlow(): Flow<List<PlaySessionEntity>>
}
