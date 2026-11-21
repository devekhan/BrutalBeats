package com.brutalbeats.player.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.playback.PlayerController
import com.brutalbeats.player.profile.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val controller: PlayerController,
    store: SettingsStore
) : ViewModel() {
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    /** null = still loading. */
    val songs: StateFlow<List<Song>?> = repo.songs

    val recentlyPlayed: StateFlow<List<Song>> =
        repo.recentlyPlayed.stateIn(viewModelScope, whileSubscribed, emptyList())
    val mostPlayed: StateFlow<List<Song>> =
        repo.mostPlayed.stateIn(viewModelScope, whileSubscribed, emptyList())
    val recentlyAdded: StateFlow<List<Song>> =
        repo.recentlyAdded.stateIn(viewModelScope, whileSubscribed, emptyList())

    val profile: StateFlow<Profile> = store.settings
        .map { Profile(it.userName, it.avatarPath) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, whileSubscribed, Profile())

    fun play(list: List<Song>, index: Int) = controller.playSongs(list, index)

    fun shuffleAll() {
        repo.songs.value?.let { controller.shuffleAll(it) }
    }

    fun rescan() = repo.rescan()
}
