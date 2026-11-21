package com.brutalbeats.player.data.repo

import com.brutalbeats.player.data.db.Favorite
import com.brutalbeats.player.data.db.FavoriteDao
import com.brutalbeats.player.data.db.PlayStat
import com.brutalbeats.player.data.db.Playlist
import com.brutalbeats.player.data.db.PlaylistDao
import com.brutalbeats.player.data.db.PlaylistWithCount
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.db.SongDao
import com.brutalbeats.player.data.db.StatsDao
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.prefs.SongSort
import com.brutalbeats.player.data.scan.MediaScanner
import com.brutalbeats.player.data.scan.ScanState
import com.brutalbeats.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AlbumInfo(
    val albumId: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    /** A song from this album, used to load its art. */
    val artSongUri: String
)

data class ArtistInfo(val name: String, val songCount: Int, val albumCount: Int)

data class FolderInfo(val path: String, val name: String, val songCount: Int)

private data class LibraryRules(
    val minSeconds: Int,
    val included: Set<String>,
    val excluded: Set<String>,
    val sort: SongSort,
    val ascending: Boolean
)

/** Sorts songs by the chosen option. Text sorts ignore case. */
fun sortSongs(list: List<Song>, sort: SongSort, ascending: Boolean): List<Song> {
    val ci = String.CASE_INSENSITIVE_ORDER
    val comparator: Comparator<Song> = when (sort) {
        SongSort.TITLE -> compareBy<Song, String>(ci) { it.title }
        SongSort.ARTIST -> compareBy<Song, String>(ci) { it.artist }.thenBy(ci) { it.title }
        SongSort.ALBUM -> compareBy<Song, String>(ci) { it.album }
            .thenBy { it.trackNumber }
            .thenBy(ci) { it.title }
        SongSort.DATE_ADDED -> compareBy<Song> { it.dateAddedSec }
        SongSort.DURATION -> compareBy<Song> { it.durationMs }
    }
    return list.sortedWith(if (ascending) comparator else comparator.reversed())
}

/** Search by title, artist or album (case-insensitive). */
fun List<Song>.searchSongs(query: String): List<Song> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter {
        it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
    }
}

private fun inFolder(folder: String, base: String): Boolean =
    folder == base || folder.startsWith("$base/")

/**
 * The single source of truth for the music library.
 * Songs come from Room; the minimum length and folder include/exclude rules from Settings are applied
 * here, so changing them is instant. [songs] is null while the first load is still running.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val songDao: SongDao,
    private val favoriteDao: FavoriteDao,
    private val statsDao: StatsDao,
    private val playlistDao: PlaylistDao,
    private val scanner: MediaScanner,
    settingsStore: SettingsStore,
    @ApplicationScope private val appScope: CoroutineScope
) {
    val scanState: StateFlow<ScanState> = scanner.state

    private val rules: Flow<LibraryRules> = settingsStore.settings
        .map {
            LibraryRules(it.minTrackSeconds, it.includedFolders, it.excludedFolders, it.songSort, it.sortAscending)
        }
        .distinctUntilChanged()

    /** Visible songs (rules applied and sorted). null = still loading. */
    val songs: StateFlow<List<Song>?> = combine(songDao.observeAll(), rules) { all, r ->
        val minMs = r.minSeconds * 1000L
        val filtered = all.filter { s ->
            s.durationMs >= minMs &&
                (r.included.isEmpty() || r.included.any { inFolder(s.folder, it) }) &&
                r.excluded.none { inFolder(s.folder, it) }
        }
        sortSongs(filtered, r.sort, r.ascending)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(appScope, SharingStarted.Eagerly, null)

    private val visible: Flow<List<Song>> = songs.filterNotNull()

    val favoriteIds: StateFlow<Set<Long>> = favoriteDao.observeAll()
        .map { list -> list.map { it.songId }.toSet() }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    val playStats: StateFlow<Map<Long, PlayStat>> = statsDao.observeAll()
        .map { list -> list.associateBy { it.songId } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    val favorites: Flow<List<Song>> = combine(visible, favoriteDao.observeAll()) { all, favs ->
        val byId = all.associateBy { it.id }
        favs.sortedByDescending { it.addedAt }.mapNotNull { byId[it.songId] }
    }.flowOn(Dispatchers.Default)

    val recentlyAdded: Flow<List<Song>> = visible
        .map { list -> list.sortedByDescending { it.dateAddedSec }.take(50) }
        .flowOn(Dispatchers.Default)

    val recentlyPlayed: Flow<List<Song>> = combine(visible, playStats) { all, stats ->
        all.filter { stats[it.id] != null }
            .sortedByDescending { stats[it.id]?.lastPlayedAt ?: 0L }
            .take(50)
    }.flowOn(Dispatchers.Default)

    val mostPlayed: Flow<List<Song>> = combine(visible, playStats) { all, stats ->
        all.filter { (stats[it.id]?.playCount ?: 0) > 0 }
            .sortedByDescending { stats[it.id]?.playCount ?: 0 }
            .take(50)
    }.flowOn(Dispatchers.Default)

    val albums: Flow<List<AlbumInfo>> = visible.map { list ->
        list.groupBy { it.albumId }.map { (id, group) ->
            val first = group.first()
            val artists = group.map { it.artist }.distinct()
            AlbumInfo(
                albumId = id,
                name = first.album,
                artist = if (artists.size == 1) artists[0] else "Various artists",
                songCount = group.size,
                artSongUri = first.uri
            )
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default)

    val artists: Flow<List<ArtistInfo>> = visible.map { list ->
        list.groupBy { it.artist }.map { (name, group) ->
            ArtistInfo(name, group.size, group.map { it.albumId }.distinct().size)
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default)

    val folders: Flow<List<FolderInfo>> = visible.map { list ->
        list.groupBy { it.folder }.map { (path, group) ->
            FolderInfo(path, File(path).name.ifBlank { path }, group.size)
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default)

    // ---- Scanning ----

    /** Starts a background rescan. */
    fun rescan() {
        appScope.launch { scanner.scan() }
    }

    /** Scans once if the cache is empty (first launch). */
    suspend fun ensureScanned() {
        if (songDao.count() == 0) scanner.scan()
    }

    // ---- Lookups for playback ----

    suspend fun songById(id: Long): Song? = songDao.get(id)

    /** Returns songs for [ids] in the same order, skipping any that no longer exist. */
    suspend fun songsByIds(ids: List<Long>): List<Song> {
        val found = ids.distinct().chunked(500).flatMap { songDao.getByIds(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    // ---- Favorites and play history ----

    suspend fun toggleFavorite(songId: Long) {
        if (favoriteDao.count(songId) > 0) {
            favoriteDao.remove(songId)
        } else {
            favoriteDao.add(Favorite(songId, System.currentTimeMillis()))
        }
    }

    suspend fun recordPlay(songId: Long, listenedMs: Long) {
        statsDao.recordPlay(songId, System.currentTimeMillis(), listenedMs)
    }

    // ---- Playlists ----

    val playlists: Flow<List<PlaylistWithCount>> = playlistDao.observeWithCounts()

    fun playlist(id: Long): Flow<Playlist?> = playlistDao.observe(id)

    fun playlistSongs(id: Long): Flow<List<Song>> =
        combine(visible, playlistDao.observeSongIds(id)) { all, ids ->
            val byId = all.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }.flowOn(Dispatchers.Default)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insert(
            Playlist(name = name.trim().ifEmpty { "New playlist" }, createdAt = System.currentTimeMillis())
        )

    suspend fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) playlistDao.rename(id, clean)
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.delete(id)

    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>) =
        playlistDao.addSongs(playlistId, songIds)

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeEntry(playlistId, songId)
}
