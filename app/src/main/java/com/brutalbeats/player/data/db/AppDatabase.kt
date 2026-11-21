package com.brutalbeats.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's local database. Every table needed through Phase 4 is already here
 * (including play events for the Phase 2 stats), so later phases should not change the schema.
 * If a later phase ever must, it will ship a proper migration so playlists are never lost.
 */
@Database(
    entities = [
        Song::class, Favorite::class, PlayStat::class, PlayEvent::class,
        Playlist::class, PlaylistEntry::class, QueueItem::class, EqPreset::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun statsDao(): StatsDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun eqPresetDao(): EqPresetDao
}
