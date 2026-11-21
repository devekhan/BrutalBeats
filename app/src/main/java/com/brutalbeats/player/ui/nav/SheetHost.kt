package com.brutalbeats.player.ui.nav

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.output.OutputViewModel
import com.brutalbeats.player.permissions.rememberAppPermissions
import com.brutalbeats.player.profile.ProfileSheet
import com.brutalbeats.player.ui.components.OutputDevicesSheet
import com.brutalbeats.player.ui.sheets.NameInputSheet
import com.brutalbeats.player.ui.sheets.PlaylistMenuSheet
import com.brutalbeats.player.ui.sheets.PlaylistPickerSheet
import com.brutalbeats.player.ui.sheets.QueueSheet
import com.brutalbeats.player.ui.sheets.SleepTimerSheet
import com.brutalbeats.player.ui.sheets.SongActionsSheet

/**
 * Draws every glass sheet. Place it as the LAST child of a root Box (after the screen content),
 * so sheets appear on top and their blur sees what is behind them.
 */
@Composable
fun BoxScope.SheetHost(
    vm: SheetsViewModel = hiltViewModel(),
    outputVm: OutputViewModel = hiltViewModel()
) {
    val sheets = LocalSheets.current
    val perms = rememberAppPermissions()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val sleep by vm.sleepState.collectAsStateWithLifecycle()
    val output by outputVm.state.collectAsStateWithLifecycle()

    LaunchedEffect(sheets.showOutput, perms.bluetooth) {
        if (sheets.showOutput) outputVm.refresh()
    }

    // ---- song menu
    val menuSong = sheets.songMenu
    SongActionsSheet(
        song = menuSong,
        playlistId = sheets.songMenuPlaylistId,
        isFavorite = menuSong != null && menuSong.id in favoriteIds,
        onDismiss = { sheets.closeSongMenu() },
        onPlayNext = {
            menuSong?.let { vm.playNext(it) }
            sheets.closeSongMenu()
        },
        onAddToQueue = {
            menuSong?.let { vm.addToQueue(it) }
            sheets.closeSongMenu()
        },
        onAddToPlaylist = {
            sheets.closeSongMenu()
            if (menuSong != null) sheets.pickPlaylistFor = listOf(menuSong)
        },
        onToggleFavorite = {
            menuSong?.let { vm.toggleFavorite(it) }
            sheets.closeSongMenu()
        },
        onRemoveFromPlaylist = {
            val pid = sheets.songMenuPlaylistId
            if (menuSong != null && pid != null) vm.removeFromPlaylist(pid, menuSong.id)
            sheets.closeSongMenu()
        }
    )

    // ---- playlist picker
    PlaylistPickerSheet(
        visible = sheets.pickPlaylistFor != null,
        playlists = playlists,
        onPick = { playlistId ->
            sheets.pickPlaylistFor?.let { vm.addToPlaylist(playlistId, it) }
            sheets.pickPlaylistFor = null
        },
        onNew = {
            val songs = sheets.pickPlaylistFor.orEmpty()
            sheets.pickPlaylistFor = null
            sheets.nameInput = NameInputRequest("New playlist", "", "Create") { name ->
                vm.createPlaylist(name, songs)
            }
        },
        onDismiss = { sheets.pickPlaylistFor = null }
    )

    // ---- playlist menu
    val menuPlaylist = sheets.playlistMenu
    PlaylistMenuSheet(
        playlist = menuPlaylist,
        onRename = {
            sheets.playlistMenu = null
            if (menuPlaylist != null) {
                sheets.nameInput = NameInputRequest(
                    "Rename playlist", menuPlaylist.playlist.name, "Save"
                ) { name -> vm.renamePlaylist(menuPlaylist.playlist.id, name) }
            }
        },
        onDelete = {
            menuPlaylist?.let { vm.deletePlaylist(it.playlist.id) }
            sheets.playlistMenu = null
        },
        onDismiss = { sheets.playlistMenu = null }
    )

    // ---- name input
    NameInputSheet(request = sheets.nameInput, onDismiss = { sheets.nameInput = null })

    // ---- queue
    QueueSheet(
        visible = sheets.showQueue,
        queue = queue,
        currentIndex = playerState.index,
        onDismiss = { sheets.showQueue = false },
        onJump = { vm.jumpTo(it) },
        onRemove = { vm.removeFromQueue(it) },
        onMove = { from, to -> vm.moveInQueue(from, to) }
    )

    // ---- sleep timer
    SleepTimerSheet(
        visible = sheets.showSleep,
        state = sleep,
        remainingMs = { vm.sleepRemainingMs() },
        onStart = { vm.startSleep(it) },
        onFinishSong = { vm.sleepFinishSong() },
        onExtend = { vm.extendSleep(it) },
        onCancel = { vm.cancelSleep() },
        onDismiss = { sheets.showSleep = false }
    )

    // ---- output devices
    OutputDevicesSheet(
        visible = sheets.showOutput,
        state = output,
        onDismiss = { sheets.showOutput = false },
        onSelect = { outputVm.select(it) },
        onOpenSystemSwitcher = { outputVm.openSystemSwitcher() },
        onRequestBluetoothPermission = { perms.requestBluetooth() }
    )

    // ---- profile
    ProfileSheet(visible = sheets.showProfile, onDismiss = { sheets.showProfile = false })
}
