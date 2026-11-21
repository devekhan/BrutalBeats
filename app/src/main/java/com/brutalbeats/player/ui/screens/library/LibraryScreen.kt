package com.brutalbeats.player.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.prefs.SongSort
import com.brutalbeats.player.data.scan.ScanState
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalTextField
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LoadingState
import com.brutalbeats.player.ui.components.SongRow
import com.brutalbeats.player.ui.components.Sticker
import com.brutalbeats.player.ui.components.brutalBox
import com.brutalbeats.player.ui.nav.Detail
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.nav.NameInputRequest
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.readable

/** The library: search, sort, and the six tabs. Opens album/artist/folder/playlist pages via [onOpenDetail]. */
@Composable
fun LibraryScreen(
    onOpenDetail: (Detail) -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalHeading("Library", style = BrutalType.h1, modifier = Modifier.weight(1f))
            when (val s = scan) {
                is ScanState.Scanning -> Sticker("Scanning ${s.found}", fill = c.accent3, rotation = -2f)
                is ScanState.Failed -> Sticker("Scan failed", fill = c.danger, rotation = 2f)
                else -> Unit
            }
            Box(Modifier.padding(start = 12.dp)) {
                BrutalIconButton(
                    onClick = vm::rescan,
                    contentDescription = "Rescan library",
                    fill = c.surface
                ) { BrutalIcon(Glyph.Refresh) }
            }
        }

        BrutalTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = "Search title, artist, album",
            leading = { BrutalIcon(Glyph.Search, tint = c.surface.readable()) },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, bottom = 8.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryTab.values().forEach { t ->
                BrutalChip(text = t.label, selected = t == tab, onClick = { vm.setTab(t) })
            }
        }

        if (tab == LibraryTab.SONGS) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val sorts = listOf(
                    SongSort.TITLE to "Title",
                    SongSort.ARTIST to "Artist",
                    SongSort.ALBUM to "Album",
                    SongSort.DATE_ADDED to "Date",
                    SongSort.DURATION to "Length"
                )
                sorts.forEach { (sort, label) ->
                    val selected = settings.songSort == sort
                    val arrow = if (!selected) "" else if (settings.sortAscending) " ↑" else " ↓"
                    BrutalChip(
                        text = label + arrow,
                        selected = selected,
                        onClick = { vm.setSort(sort) },
                        selectedFill = c.accent3
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                LibraryTab.SONGS -> {
                    val songs by vm.songs.collectAsStateWithLifecycle()
                    SongListContent(
                        songs = songs,
                        query = query,
                        emptyTitle = "No music found",
                        emptyMessage = "Add songs to your phone, then rescan.",
                        vm = vm,
                        showRescan = true
                    )
                }
                LibraryTab.FAVORITES -> {
                    val favorites by vm.favorites.collectAsStateWithLifecycle()
                    SongListContent(
                        songs = favorites,
                        query = query,
                        emptyTitle = "No favorites yet",
                        emptyMessage = "Tap the heart on any song and it lands here.",
                        vm = vm,
                        showRescan = false
                    )
                }
                LibraryTab.ALBUMS -> AlbumsContent(vm, onOpenDetail)
                LibraryTab.ARTISTS -> ArtistsContent(vm, onOpenDetail)
                LibraryTab.FOLDERS -> FoldersContent(vm, onOpenDetail)
                LibraryTab.PLAYLISTS -> PlaylistsContent(vm, onOpenDetail)
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SongListContent(
    songs: List<Song>?,
    query: String,
    emptyTitle: String,
    emptyMessage: String,
    vm: LibraryViewModel,
    showRescan: Boolean
) {
    val sheets = LocalSheets.current
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current

    when {
        songs == null -> CenterBox { LoadingState() }
        songs.isEmpty() -> CenterBox {
            if (query.isNotBlank()) {
                EmptyState(
                    title = "No matches",
                    message = "Nothing matches \"$query\". Try another word.",
                    glyph = Glyph.Search
                )
            } else {
                EmptyState(
                    title = emptyTitle,
                    message = emptyMessage,
                    glyph = Glyph.Music,
                    actionLabel = if (showRescan) "Rescan" else null,
                    onAction = if (showRescan) vm::rescan else null
                )
            }
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp)
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${songs.size} SONGS",
                        style = BrutalType.label,
                        color = Brutal.colors.textMuted,
                        modifier = Modifier.weight(1f)
                    )
                    BrutalButton(
                        text = "Shuffle",
                        onClick = { vm.shuffle(songs) },
                        fill = Brutal.colors.accent2,
                        leading = { BrutalIcon(Glyph.Shuffle) }
                    )
                }
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentId,
                    isFavorite = song.id in favoriteIds,
                    onClick = { vm.play(songs, index) },
                    onToggleFavorite = { vm.toggleFavorite(song) },
                    onMore = { sheets.openSongMenu(song) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsContent(vm: LibraryViewModel, onOpenDetail: (Detail) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current
    val list = albums
    when {
        list == null -> CenterBox { LoadingState() }
        list.isEmpty() -> CenterBox {
            EmptyState("No albums", "Albums appear once your songs are scanned.", glyph = Glyph.Album)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(list, key = { it.albumId }) { album ->
                Column(
                    Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Open album ${album.name}"
                        ) { onOpenDetail(Detail.Album(album.albumId, album.name)) }
                ) {
                    AlbumArt(
                        uri = album.artSongUri,
                        albumId = album.albumId,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        sizePx = 320
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = album.name,
                        style = BrutalType.bodyBold,
                        color = Brutal.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} · ${album.songCount}",
                        style = BrutalType.caption,
                        color = Brutal.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** A tappable row with a colored icon block: used for artists, folders and playlists. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoRow(
    glyph: Glyph,
    title: String,
    subtitle: String,
    blockColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val c = Brutal.colors
    val shape = RoundedCornerShape(Brutal.dims.corner)
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .brutalBox(c.surface, shape, Brutal.dims.border, c.ink, 4.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .brutalBox(blockColor, RoundedCornerShape(6.dp), Brutal.dims.border, c.ink, 2.dp),
                contentAlignment = Alignment.Center
            ) { BrutalIcon(glyph, tint = Color.Black) }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = BrutalType.bodyBold,
                    color = c.surface.readable(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = BrutalType.caption,
                    color = c.surface.readable().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistsContent(vm: LibraryViewModel, onOpenDetail: (Detail) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current
    val c = Brutal.colors
    val list = artists
    when {
        list == null -> CenterBox { LoadingState() }
        list.isEmpty() -> CenterBox {
            EmptyState("No artists", "Artists appear once your songs are scanned.", glyph = Glyph.Artist)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp)) {
            items(list, key = { it.name }) { artist ->
                InfoRow(
                    glyph = Glyph.Artist,
                    title = artist.name,
                    subtitle = "${artist.songCount} songs · ${artist.albumCount} albums",
                    blockColor = c.accent2,
                    onClick = { onOpenDetail(Detail.Artist(artist.name)) }
                )
            }
        }
    }
}

@Composable
private fun FoldersContent(vm: LibraryViewModel, onOpenDetail: (Detail) -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current
    val c = Brutal.colors
    val list = folders
    when {
        list == null -> CenterBox { LoadingState() }
        list.isEmpty() -> CenterBox {
            EmptyState("No folders", "Folders appear once your songs are scanned.", glyph = Glyph.Folder)
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp)) {
            items(list, key = { it.path }) { folder ->
                InfoRow(
                    glyph = Glyph.Folder,
                    title = folder.name,
                    subtitle = "${folder.songCount} songs · ${folder.path}",
                    blockColor = c.accent4,
                    onClick = { onOpenDetail(Detail.Folder(folder.path, folder.name)) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsContent(vm: LibraryViewModel, onOpenDetail: (Detail) -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val sheets = LocalSheets.current
    val bottom = LocalBottomInset.current
    val c = Brutal.colors
    val list = playlists
    val newPlaylist = {
        sheets.nameInput = NameInputRequest("New playlist", "", "Create") { name -> vm.createPlaylist(name) }
    }
    when {
        list == null -> CenterBox { LoadingState() }
        list.isEmpty() -> CenterBox {
            EmptyState(
                title = "No playlists",
                message = "Make one and fill it with your favorite songs.",
                glyph = Glyph.Playlist,
                actionLabel = "New playlist",
                onAction = newPlaylist
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp)) {
            item(key = "new") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, bottom = 12.dp)) {
                    BrutalButton(
                        text = "New playlist",
                        onClick = newPlaylist,
                        fill = c.accent,
                        leading = { BrutalIcon(Glyph.Plus) }
                    )
                }
            }
            items(list, key = { it.playlist.id }) { item ->
                InfoRow(
                    glyph = Glyph.Playlist,
                    title = item.playlist.name,
                    subtitle = "${item.songCount} songs",
                    blockColor = c.accent3,
                    onClick = { onOpenDetail(Detail.Playlist(item.playlist.id, item.playlist.name)) },
                    onLongClick = { sheets.playlistMenu = item }
                )
            }
        }
    }
}
