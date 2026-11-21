package com.brutalbeats.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT id FROM songs")
    suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(songs: List<Song>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): Song?
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("SELECT COUNT(*) FROM favorites WHERE songId = :songId")
    suspend fun count(songId: Long): Int
}

@Dao
abstract class StatsDao {
    @Query("SELECT * FROM play_stats")
    abstract fun observeAll(): Flow<List<PlayStat>>

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    abstract suspend fun get(songId: Long): PlayStat?

    @Upsert
    abstract suspend fun upsert(stat: PlayStat)

    @Insert
    abstract suspend fun insertEvent(event: PlayEvent)

    /** Counts one listen: bumps the song's play count and logs an event. */
    @Transaction
    open suspend fun recordPlay(songId: Long, now: Long, listenedMs: Long) {
        val current = get(songId)
        upsert(PlayStat(songId, (current?.playCount ?: 0) + 1, now))
        insertEvent(PlayEvent(songId = songId, playedAt = now, listenedMs = listenedMs))
    }
}

@Dao
abstract class PlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.createdAt DESC"
    )
    abstract fun observeWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observe(id: Long): Flow<Playlist?>

    @Insert
    abstract suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT songId FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    abstract fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Insert
    abstract suspend fun insertEntry(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeEntry(playlistId: Long, songId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun countEntry(playlistId: Long, songId: Long): Int

    /** Adds songs to the end of a playlist, skipping ones already in it. */
    @Transaction
    open suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        var position = maxPosition(playlistId) + 1
        for (id in songIds) {
            if (countEntry(playlistId, id) == 0) {
                insertEntry(PlaylistEntry(playlistId = playlistId, songId = id, position = position))
                position++
            }
        }
    }
}

@Dao
abstract class QueueDao {
    @Query("SELECT songId FROM queue_items ORDER BY position")
    abstract suspend fun getAll(): List<Long>

    @Query("DELETE FROM queue_items")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(items: List<QueueItem>)

    /** Replaces the saved queue with [ids] in order. */
    @Transaction
    open suspend fun replace(ids: List<Long>) {
        clear()
        insertAll(ids.mapIndexed { index, id -> QueueItem(index, id) })
    }
}

@Dao
interface EqPresetDao {
    @Query("SELECT * FROM eq_presets ORDER BY id")
    fun observeAll(): Flow<List<EqPreset>>

    @Insert
    suspend fun insert(preset: EqPreset): Long

    @Query("UPDATE eq_presets SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun delete(id: Long)
}
