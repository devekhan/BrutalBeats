package com.brutalbeats.player.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached copy of a MediaStore audio file. Rebuilt by every scan. */
@Entity(tableName = "songs", indices = [Index("albumId"), Index("folder"), Index("dateAddedSec")])
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: String,
    val folder: String,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val trackNumber: Int,
    val year: Int,
    val missingMetadata: Boolean
)

@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val songId: Long, val addedAt: Long)

@Entity(tableName = "play_stats")
data class PlayStat(@PrimaryKey val songId: Long, val playCount: Int, val lastPlayedAt: Long)

/** One row per listen. Powers "most played this month" and the stats page in Phase 2. */
@Entity(tableName = "play_events", indices = [Index("songId"), Index("playedAt")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
    val listenedMs: Long
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int
)

/** The saved play queue, so the app can resume after a restart. */
@Entity(tableName = "queue_items")
data class QueueItem(@PrimaryKey val position: Int, val songId: Long)

/** User-saved equalizer preset. [levelsCsv] holds one millibel value per band, comma separated. */
@Entity(tableName = "eq_presets")
data class EqPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bandCount: Int,
    val levelsCsv: String
)

data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val songCount: Int
)
