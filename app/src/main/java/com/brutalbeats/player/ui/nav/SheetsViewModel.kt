package com.brutalbeats.player.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.PlaylistWithCount
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.playback.PlayerController
import com.brutalbeats.player.playback.PlayerUiState
import com.brutalbeats.player.playback.SleepState
import com.brutalbeats.player.playback.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the play queue. [song] is null if the file no longer exists. */
data class QueueEntry(val index: Int, val id: Long, val song: Song?)

/** Logic behind every glass sheet: song menu, playlists, queue, sleep timer. */
@HiltViewModel
class SheetsViewModel @Inject constructor(
    private val controller: PlayerController,
    private val repo: LibraryRepository,
    private val sleepTimer: SleepTimer
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = controller.state
    val favoriteIds: StateFlow<Set<Long>> = repo.favoriteIds
    val sleepState: StateFlow<SleepState> = sleepTimer.state

    val playlists: StateFlow<List<PlaylistWithCount>> = repo.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queue: StateFlow<List<QueueEntry>> = combine(
        controller.state.map { it.queueIds }.distinctUntilChanged(),
        controller.queueSongs
    ) { ids, songs ->
        val byId = songs.associateBy { it.id }
        ids.mapIndexed { index, id -> QueueEntry(index, id, byId[id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- song actions
    fun playNext(song: Song) = controller.playNext(song)
    fun addToQueue(song: Song) = controller.addToQueue(song)
    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repo.toggleFavorite(song.id) }
    }

    // ---- playlists
    fun addToPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { repo.addToPlaylist(playlistId, songs.map { it.id }) }
    }

    fun createPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch {
            val id = repo.createPlaylist(name)
            if (songs.isNotEmpty()) repo.addToPlaylist(id, songs.map { it.id })
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { repo.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repo.deletePlaylist(id) }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repo.removeFromPlaylist(playlistId, songId) }
    }

    // ---- queue
    fun jumpTo(index: Int) = controller.jumpTo(index)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = controller.moveInQueue(from, to)

    // ---- sleep timer
    fun startSleep(minutes: Int) = sleepTimer.startMinutes(minutes)
    fun extendSleep(minutes: Int) = sleepTimer.extend(minutes)
    fun sleepFinishSong() = sleepTimer.finishCurrentSong()
    fun cancelSleep() = sleepTimer.cancel()
    fun sleepRemainingMs(): Long = sleepTimer.remainingMs()
}
