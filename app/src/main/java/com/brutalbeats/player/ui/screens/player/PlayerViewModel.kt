package com.brutalbeats.player.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.playback.PlayerController
import com.brutalbeats.player.playback.PlayerUiState
import com.brutalbeats.player.playback.SleepState
import com.brutalbeats.player.playback.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** Backs the mini player and the Now Playing screen. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    private val repo: LibraryRepository,
    sleepTimer: SleepTimer
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = controller.state
    val song: StateFlow<Song?> = controller.currentSong
    val favoriteIds: StateFlow<Set<Long>> = repo.favoriteIds
    val sleepState: StateFlow<SleepState> = sleepTimer.state

    /** Create it once with remember { } in the screen, never inline. */
    fun positionFlow(): Flow<Long> = controller.positionFlow()

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()
    fun clearError() = controller.clearError()

    /** 1x -> 1.25x -> 1.5x -> 2x -> 0.5x -> 0.75x -> 1x */
    fun cycleSpeed(current: Float) {
        val steps = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)
        val i = steps.indexOfFirst { abs(it - current) < 0.01f }
        controller.setSpeed(steps[(i + 1) % steps.size])
    }

    fun toggleFavorite() {
        val current = song.value ?: return
        viewModelScope.launch { repo.toggleFavorite(current.id) }
    }
}
