package com.brutalbeats.player.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.brutalbeats.player.data.db.PlaylistWithCount
import com.brutalbeats.player.data.db.Song

/** Asks the user for a name (new playlist, rename playlist). */
data class NameInputRequest(
    val title: String,
    val initial: String,
    val confirmLabel: String,
    val onConfirm: (String) -> Unit
)

/**
 * Which glass sheets are open. Any screen can open one (sheets.showQueue = true);
 * SheetHost draws them on top of everything so the blur sees the right background.
 */
@Stable
class AppSheets {
    var songMenu by mutableStateOf<Song?>(null)
    var songMenuPlaylistId by mutableStateOf<Long?>(null)
    var pickPlaylistFor by mutableStateOf<List<Song>?>(null)
    var playlistMenu by mutableStateOf<PlaylistWithCount?>(null)
    var nameInput by mutableStateOf<NameInputRequest?>(null)
    var showQueue by mutableStateOf(false)
    var showSleep by mutableStateOf(false)
    var showOutput by mutableStateOf(false)
    var showProfile by mutableStateOf(false)

    fun openSongMenu(song: Song, playlistId: Long? = null) {
        songMenu = song
        songMenuPlaylistId = playlistId
    }

    fun closeSongMenu() {
        songMenu = null
    }
}

val LocalSheets = staticCompositionLocalOf<AppSheets> { error("AppSheets not provided") }
