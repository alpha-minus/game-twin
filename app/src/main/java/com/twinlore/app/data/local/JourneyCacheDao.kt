package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the Achievement Journey on-disk cache: per-game snapshots and the
 * per-user completion list. Both tables are pure caches (no FKs) so they can
 * be wiped and repopulated independently of vault data.
 */
@Dao
interface JourneyCacheDao {
    @Query("SELECT * FROM journey_game_cache WHERE userName = :userName AND gameId = :gameId")
    suspend fun getGame(userName: String, gameId: Int): JourneyGameCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(entity: JourneyGameCacheEntity)

    @Query("SELECT COUNT(*) FROM journey_game_cache")
    suspend fun countGames(): Int

    /** Deletes the [n] oldest rows (by fetchedAt) to bound cache growth. */
    @Query(
        "DELETE FROM journey_game_cache WHERE (userName || ':' || gameId) IN " +
            "(SELECT (userName || ':' || gameId) FROM journey_game_cache ORDER BY fetchedAt ASC LIMIT :n)"
    )
    suspend fun evictOldestGames(n: Int)

    /**
     * Which accounts the cached rows belong to. Read before a load so a cache written for a
     * previous RetroAchievements account can be dropped rather than aged out one row at a time:
     * the rows are their own record of whose they are, so nothing separate has to be kept in step.
     */
    @Query("SELECT DISTINCT userName FROM journey_game_cache")
    suspend fun cachedGameUsers(): List<String>

    /**
     * Every cache key with a completion list, newest fetch first.
     *
     * The order is what makes the rival recents a recents list: the panel offers a bounded number
     * of chips, so alphabetical order meant a rival late in the alphabet never got one - and a
     * chip is the only way to forget one individually.
     */
    @Query("SELECT DISTINCT userName FROM journey_completion_cache ORDER BY fetchedAt DESC")
    suspend fun cachedCompletionUsers(): List<String>

    @Query("SELECT COUNT(*) FROM journey_completion_cache")
    suspend fun countCompletion(): Int

    /**
     * Deletes the [n] oldest completion rows.
     *
     * The table gained a row per rival when rival data started persisting, and a completion list
     * is not small - up to a couple of thousand rows of it serialized - so without this it is the
     * one table here that grows for as long as a user keeps comparing against new people.
     */
    @Query(
        "DELETE FROM journey_completion_cache WHERE userName IN " +
            "(SELECT userName FROM journey_completion_cache ORDER BY fetchedAt ASC LIMIT :n)"
    )
    suspend fun evictOldestCompletion(n: Int)

    @Query("SELECT * FROM journey_completion_cache WHERE userName = :userName")
    suspend fun getCompletion(userName: String): JourneyCompletionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(entity: JourneyCompletionCacheEntity)

    /**
     * Everything cached for one cache key. Used to forget a single rival - their key is the
     * signed-in account's own plus their name - without touching anybody else's rows.
     */
    @Query("DELETE FROM journey_game_cache WHERE userName = :userName")
    suspend fun deleteGamesFor(userName: String)

    @Query("DELETE FROM journey_completion_cache WHERE userName = :userName")
    suspend fun deleteCompletionFor(userName: String)

    @Query("DELETE FROM journey_game_cache")
    suspend fun clearGames()

    @Query("DELETE FROM journey_completion_cache")
    suspend fun clearCompletion()
}
