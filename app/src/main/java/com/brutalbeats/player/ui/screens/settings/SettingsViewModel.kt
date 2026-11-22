package com.brutalbeats.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.SongDao
import com.brutalbeats.player.data.prefs.AppSettings
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.prefs.ThemeMode
import com.brutalbeats.player.data.repo.FolderInfo
import com.brutalbeats.player.data.repo.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val repo: LibraryRepository,
    songDao: SongDao
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Every folder that has songs, even ones you have hidden (so you can un-hide them). */
    val allFolders: StateFlow<List<FolderInfo>?> = songDao.observeAll()
        .map { all ->
            all.groupBy { it.folder }.map { (path, group) ->
                FolderInfo(path, File(path).name.ifBlank { path }, group.size)
            }.sortedBy { it.name.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(mode: ThemeMode) = write { it[SettingsStore.Keys.THEME] = mode.name }
    fun setHighContrast(on: Boolean) = write { it[SettingsStore.Keys.HIGH_CONTRAST] = on }
    fun setAccent(argb: Int) = write { it[SettingsStore.Keys.ACCENT] = argb }
    fun setGlass(value: Float) = write { it[SettingsStore.Keys.GLASS] = value.coerceIn(0f, 1f) }
    fun setMinTrackSeconds(seconds: Int) = write { it[SettingsStore.Keys.MIN_TRACK] = seconds }
    fun setCrossfade(seconds: Int) = write { it[SettingsStore.Keys.CROSSFADE] = seconds.coerceIn(0, 12) }
    fun setFadeOnPause(on: Boolean) = write { it[SettingsStore.Keys.FADE_ON_PAUSE] = on }
    fun rescan() = repo.rescan()

    /** HIDE: songs in this folder (and its subfolders) disappear from the app. */
    fun toggleHidden(path: String) {
        viewModelScope.launch {
            val current = store.settings.first()
            val excluded = current.excludedFolders.toMutableSet()
            if (!excluded.remove(path)) excluded.add(path)
            val included = current.includedFolders - path
            store.edit {
                it[SettingsStore.Keys.EXCLUDED] = excluded
                it[SettingsStore.Keys.INCLUDED] = included
            }
        }
    }

    /** ONLY: when any folder is marked ONLY, the app uses just those folders. */
    fun toggleOnly(path: String) {
        viewModelScope.launch {
            val current = store.settings.first()
            val included = current.includedFolders.toMutableSet()
            if (!included.remove(path)) included.add(path)
            val excluded = current.excludedFolders - path
            store.edit {
                it[SettingsStore.Keys.INCLUDED] = included
                it[SettingsStore.Keys.EXCLUDED] = excluded
            }
        }
    }

    fun resetFolderRules() = write {
        it[SettingsStore.Keys.INCLUDED] = emptySet()
        it[SettingsStore.Keys.EXCLUDED] = emptySet()
    }

    private fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { store.edit(block) }
    }
}
