package com.brutalbeats.player.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LoadingState
import com.brutalbeats.player.ui.components.SongRow
import com.brutalbeats.player.ui.nav.Detail
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType

/** The songs of one album, artist, folder or playlist. */
@Composable
fun DetailScreen(
    detail: Detail,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val sheets = LocalSheets.current
    val bottom = LocalBottomInset.current
    val flow = remember(detail) { vm.detailSongs(detail) }
    val songs by flow.collectAsStateWithLifecycle(initialValue = null)
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val list = songs
    val playlistId = (detail as? Detail.Playlist)?.id

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalIconButton(onClick = onBack, contentDescription = "Go back", fill = c.surface) {
                BrutalIcon(Glyph.Back)
            }
            BrutalHeading(
                text = detail.title,
                style = BrutalType.h2,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
        }
        if (list != null && list.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${list.size} SONGS",
                    style = BrutalType.label,
                    color = c.textMuted,
                    modifier = Modifier.weight(1f)
                )
                BrutalButton(
                    text = "Play",
                    onClick = { vm.play(list, 0) },
                    leading = { BrutalIcon(Glyph.Play) }
                )
                BrutalButton(
                    text = "Shuffle",
                    onClick = { vm.shuffle(list) },
                    fill = c.accent2,
                    leading = { BrutalIcon(Glyph.Shuffle) }
                )
            }
        }
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                title = if (playlistId != null) "Empty playlist" else "Nothing here",
                message = if (playlistId != null) {
                    "Open the menu on any song and choose Add to playlist."
                } else {
                    "These songs may have been removed or hidden by a folder rule."
                },
                glyph = Glyph.Music
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp)
            ) {
                itemsIndexed(list, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == currentId,
                        isFavorite = song.id in favoriteIds,
                        onClick = { vm.play(list, index) },
                        onToggleFavorite = { vm.toggleFavorite(song) },
                        onMore = { sheets.openSongMenu(song, playlistId) }
                    )
                }
            }
        }
    }
}
