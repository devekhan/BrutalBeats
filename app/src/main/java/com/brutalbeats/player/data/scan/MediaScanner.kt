package com.brutalbeats.player.data.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.db.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val found: Int) : ScanState
    data class Done(val total: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Reads audio files from MediaStore into the Room cache. Runs on a background thread.
 * Short clips are NOT dropped here: the minimum length is applied when reading, so changing
 * it in Settings is instant and needs no rescan.
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    suspend fun scan() {
        if (!mutex.tryLock()) return // a scan is already running
        try {
            _state.value = ScanState.Scanning(0)
            val songs = withContext(Dispatchers.IO) { querySongs() }
            withContext(Dispatchers.IO) { persist(songs) }
            _state.value = ScanState.Done(songs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            _state.value = ScanState.Failed("Brutal Beats needs permission to read your audio files.")
        } catch (e: Exception) {
            _state.value = ScanState.Failed(e.message ?: "Scanning failed.")
        } finally {
            mutex.unlock()
        }
    }

    @Suppress("DEPRECATION")
    private fun querySongs(): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA
        )
        // Everything playable except ringtones, alarms and notification sounds.
        val selection = "${MediaStore.Audio.Media.DURATION} > 0 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"

        val out = ArrayList<Song>()
        val cursor = context.contentResolver.query(collection, projection, selection, null, null)
            ?: throw IllegalStateException("Could not read the media library.")
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val duration = c.getLong(iDuration)
                if (duration <= 0L) continue
                val id = c.getLong(iId)
                val path = c.getString(iData).orEmpty()

                val rawTitle = c.getString(iTitle).orEmpty().trim()
                val rawArtist = c.getString(iArtist).orEmpty().trim()
                val rawAlbum = c.getString(iAlbum).orEmpty().trim()
                val artistUnknown = rawArtist.isEmpty() || rawArtist == "<unknown>"
                val albumUnknown = rawAlbum.isEmpty() || rawAlbum == "<unknown>"
                val title = rawTitle.ifEmpty {
                    File(path).nameWithoutExtension.ifEmpty { "Unknown title" }
                }

                out.add(
                    Song(
                        id = id,
                        title = title,
                        artist = if (artistUnknown) "Unknown artist" else rawArtist,
                        album = if (albumUnknown) "Unknown album" else rawAlbum,
                        albumId = c.getLong(iAlbumId),
                        durationMs = duration,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ).toString(),
                        folder = File(path).parent.orEmpty(),
                        dateAddedSec = c.getLong(iAdded),
                        sizeBytes = c.getLong(iSize),
                        mimeType = c.getString(iMime) ?: "audio/*",
                        trackNumber = c.getInt(iTrack) % 1000, // MediaStore stores disc*1000+track
                        year = c.getInt(iYear),
                        missingMetadata = rawTitle.isEmpty() || artistUnknown || albumUnknown
                    )
                )
                if (out.size % 200 == 0) _state.value = ScanState.Scanning(out.size)
            }
        }
        return out
    }

    private suspend fun persist(songs: List<Song>) {
        songs.chunked(500).forEach { songDao.upsertAll(it) }
        val live = HashSet<Long>(songs.size * 2)
        songs.forEach { live.add(it.id) }
        val stale = songDao.allIds().filter { it !in live }
        stale.chunked(500).forEach { songDao.deleteByIds(it) }
    }
}
