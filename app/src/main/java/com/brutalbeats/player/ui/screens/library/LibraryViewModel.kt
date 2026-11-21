package com.brutalbeats.player.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.PlaylistWithCount
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.prefs.AppSettings
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.prefs.SongSort
import com.brutalbeats.player.data.repo.AlbumInfo
import com.brutalbeats.player.data.repo.ArtistInfo
import com.brutalbeats.player.data.repo.FolderInfo
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.data.repo.searchSongs
import com.brutalbeats.player.data.scan.ScanState
import com.brutalbeats.player.playback.PlayerController
import com.brutalbeats.player.ui.nav.Detail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites")
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val controller: PlayerController,
    private val store: SettingsStore
) : ViewModel() {

    val tab = MutableStateFlow(LibraryTab.SONGS)
    val query = MutableStateFlow("")

    private val debouncedQuery: Flow<String> = query.debounce(150).distinctUntilChanged()
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, whileSubscribed, AppSettings())
    val scanState: StateFlow<ScanState> = repo.scanState
    val favoriteIds: StateFlow<Set<Long>> = repo.favoriteIds
    val currentId: StateFlow<Long> = controller.state
        .map { it.currentId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, whileSubscribed, -1L)

    /** null = still loading. */
    val songs: StateFlow<List<Song>?> = combine(repo.songs, debouncedQuery) { all, q ->
        all?.searchSongs(q)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, whileSubscribed, null)

    val favorites: StateFlow<List<Song>?> = combine(repo.favorites, debouncedQuery) { all, q ->
        all.searchSongs(q)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, whileSubscribed, null)

    val albums: StateFlow<List<AlbumInfo>?> = combine(repo.albums, debouncedQuery) { all, q ->
        if (q.isBlank()) all else all.filter {
            it.name.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, whileSubscribed, null)

    val artists: StateFlow<List<ArtistInfo>?> = combine(repo.artists, debouncedQuery) { all, q ->
        if (q.isBlank()) all else all.filter { it.name.contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, whileSubscribed, null)

    val folders: StateFlow<List<FolderInfo>?> = combine(repo.folders, debouncedQuery) { all, q ->
        if (q.isBlank()) all else all.filter {
            it.name.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, whileSubscribed, null)

    val playlists: StateFlow<List<PlaylistWithCount>?> =
        combine(repo.playlists, debouncedQuery) { all, q ->
            if (q.isBlank()) all else all.filter { it.playlist.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, whileSubscribed, null)

    fun setTab(value: LibraryTab) {
        tab.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** Tapping the active sort flips its direction; tapping another sort selects it. */
    fun setSort(sort: SongSort) {
        viewModelScope.launch {
            val current = store.settings.first()
            store.edit {
                if (current.songSort == sort) {
                    it[SettingsStore.Keys.SORT_ASC] = !current.sortAscending
                } else {
                    it[SettingsStore.Keys.SORT] = sort.name
                    it[SettingsStore.Keys.SORT_ASC] = sort != SongSort.DATE_ADDED
                }
            }
        }
    }

    fun rescan() = repo.rescan()

    /** Scans once if the library cache is empty. Call when audio permission is granted. */
    fun ensureScanned() {
        viewModelScope.launch { repo.ensureScanned() }
    }

    fun play(list: List<Song>, index: Int) = controller.playSongs(list, index)

    fun shuffle(list: List<Song>) = controller.shuffleAll(list)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repo.toggleFavorite(song.id) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repo.createPlaylist(name) }
    }

    /** The songs shown on an album / artist / folder / playlist page. */
    fun detailSongs(detail: Detail): Flow<List<Song>> = when (detail) {
        is Detail.Album -> repo.songs.filterNotNull().map { all ->
            all.filter { it.albumId == detail.id }.sortedBy { it.trackNumber }
        }
        is Detail.Artist -> repo.songs.filterNotNull().map { all ->
            all.filter { it.artist == detail.name }
                .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
        }
        is Detail.Folder -> repo.songs.filterNotNull().map { all ->
            all.filter { it.folder == detail.path }
        }
        is Detail.Playlist -> repo.playlistSongs(detail.id)
    }.flowOn(Dispatchers.Default)
}
