package com.brutalbeats.player.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.profile.Avatar
import com.brutalbeats.player.profile.Greeting
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LoadingState
import com.brutalbeats.player.ui.components.brutalBox
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType

/** Home: greeting + profile avatar, shuffle-all, and quick shelves (recently played, most played, new). */
@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val sheets = LocalSheets.current
    val bottom = LocalBottomInset.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val all = songs

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottom + 16.dp)
    ) {
        item(key = "top") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.weight(1f)) { Greeting(name = profile.name) }
                Avatar(
                    name = profile.name,
                    avatarPath = profile.avatarPath,
                    size = 52.dp,
                    showEditBadge = true,
                    onClick = { sheets.showProfile = true }
                )
            }
        }

        when {
            all == null -> item(key = "loading") { LoadingState() }
            all.isEmpty() -> item(key = "empty") {
                EmptyState(
                    title = "No music yet",
                    message = "Put some songs on your phone, then scan for them.",
                    glyph = Glyph.Music,
                    actionLabel = "Scan now",
                    onAction = vm::rescan
                )
            }
            else -> {
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BrutalButton(
                            text = "Shuffle all",
                            onClick = vm::shuffleAll,
                            fill = c.accent2,
                            modifier = Modifier.weight(1f),
                            leading = { BrutalIcon(Glyph.Shuffle) }
                        )
                        BrutalButton(
                            text = "Library",
                            onClick = onOpenLibrary,
                            fill = c.accent4,
                            modifier = Modifier.weight(1f),
                            leading = { BrutalIcon(Glyph.Folder) }
                        )
                    }
                }
                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "recent") { Shelf("Recently played", recentlyPlayed, vm::play) }
                }
                if (mostPlayed.isNotEmpty()) {
                    item(key = "most") { Shelf("Most played", mostPlayed, vm::play) }
                }
                if (recentlyAdded.isNotEmpty()) {
                    item(key = "added") { Shelf("Recently added", recentlyAdded, vm::play) }
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val c = Brutal.colors
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier.padding(start = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(14.dp).brutalBox(c.accent, RoundedCornerShape(2.dp), 3.dp, c.ink, 2.dp))
            BrutalHeading(title, style = BrutalType.h3)
        }
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongCard(song = song, onClick = { onPlay(songs, index) })
            }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    val c = Brutal.colors
    Column(
        Modifier
            .width(136.dp)
            .padding(end = 4.dp, bottom = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Play ${song.title}",
                onClick = onClick
            )
    ) {
        AlbumArt(
            uri = song.uri,
            albumId = song.albumId,
            modifier = Modifier.size(132.dp),
            sizePx = 320
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = song.title,
            style = BrutalType.bodyBold,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = BrutalType.caption,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
