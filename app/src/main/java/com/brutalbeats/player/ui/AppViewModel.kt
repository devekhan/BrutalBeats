package com.brutalbeats.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.prefs.AppSettings
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.data.scan.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repo: LibraryRepository,
    store: SettingsStore
) : ViewModel() {

    /** null until the saved settings are read (a split second at launch). */
    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True only during the very first scan, when there is nothing to show yet. */
    val firstScan: StateFlow<Boolean> = combine(repo.songs, repo.scanState) { songs, scan ->
        songs.isNullOrEmpty() && scan is ScanState.Scanning
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Looks for new songs every time the app opens. */
    fun scanOnStart() = repo.rescan()
}
