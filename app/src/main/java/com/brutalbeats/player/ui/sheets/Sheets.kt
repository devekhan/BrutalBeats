package com.brutalbeats.player.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutalbeats.player.data.db.PlaylistWithCount
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSheet
import com.brutalbeats.player.ui.components.BrutalTextField
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.brutalPress
import com.brutalbeats.player.ui.nav.NameInputRequest
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.readable

/** Remembers the last non-null value so a sheet keeps its content while it slides away. */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/** Full-width action button used inside sheets. */
@Composable
fun ActionRow(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Brutal.colors.surface
) {
    val c = Brutal.colors
    val shape = RoundedCornerShape(Brutal.dims.corner)
    val fg = fill.readable()
    Box(modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .brutalPress(onClick, shape, 4.dp, c.ink)
                .clip(shape)
                .background(fill)
                .border(Brutal.dims.border, c.ink, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalIcon(glyph, tint = fg)
            Text(
                text = label.uppercase(),
                style = BrutalType.label.copy(fontSize = 13.sp),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit, closeLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrutalHeading(text = title, style = BrutalType.h2, modifier = Modifier.weight(1f), maxLines = 2)
        BrutalIconButton(onClick = onDismiss, contentDescription = closeLabel, fill = Brutal.colors.surface) {
            BrutalIcon(Glyph.Close)
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** Menu for one song: play next, queue, playlist, favorite (and "remove from playlist" inside a playlist). */
@Composable
fun BoxScope.SongActionsSheet(
    song: Song?,
    playlistId: Long?,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromPlaylist: () -> Unit
) {
    val c = Brutal.colors
    val shown = rememberLast(song)
    BrutalSheet(visible = song != null, onDismiss = onDismiss) {
        if (shown != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AlbumArt(
                    uri = shown.uri,
                    albumId = shown.albumId,
                    modifier = Modifier.size(60.dp),
                    sizePx = 160,
                    shadow = 3.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = shown.title,
                        style = BrutalType.h3,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = shown.artist,
                        style = BrutalType.caption,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BrutalIconButton(onClick = onDismiss, contentDescription = "Close menu", fill = c.surface) {
                    BrutalIcon(Glyph.Close)
                }
            }
            Spacer(Modifier.height(18.dp))
            ActionRow(Glyph.Next, "Play next", onPlayNext)
            ActionRow(Glyph.Queue, "Add to queue", onAddToQueue)
            ActionRow(Glyph.Playlist, "Add to playlist", onAddToPlaylist)
            ActionRow(
                glyph = if (isFavorite) Glyph.HeartFilled else Glyph.Heart,
                label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = onToggleFavorite,
                fill = if (isFavorite) c.accent2 else c.surface
            )
            if (playlistId != null) {
                ActionRow(Glyph.Trash, "Remove from this playlist", onRemoveFromPlaylist, fill = c.danger)
            }
        }
    }
}

/** Pick a playlist to add songs to, or create a new one. */
@Composable
fun BoxScope.PlaylistPickerSheet(
    visible: Boolean,
    playlists: List<PlaylistWithCount>,
    onPick: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Brutal.colors
    BrutalSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader("Add to playlist", onDismiss, "Close playlist picker")
        ActionRow(Glyph.Plus, "New playlist", onNew, fill = c.accent)
        playlists.forEach { p ->
            ActionRow(
                glyph = Glyph.Playlist,
                label = "${p.playlist.name} (${p.songCount})",
                onClick = { onPick(p.playlist.id) }
            )
        }
    }
}

/** Rename or delete a playlist. */
@Composable
fun BoxScope.PlaylistMenuSheet(
    playlist: PlaylistWithCount?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Brutal.colors
    val shown = rememberLast(playlist)
    BrutalSheet(visible = playlist != null, onDismiss = onDismiss) {
        SheetHeader(shown?.playlist?.name ?: "Playlist", onDismiss, "Close playlist menu")
        ActionRow(Glyph.Edit, "Rename", onRename)
        ActionRow(Glyph.Trash, "Delete playlist", onDelete, fill = c.danger)
    }
}

/** Asks for a name. Used for new and renamed playlists. */
@Composable
fun BoxScope.NameInputSheet(
    request: NameInputRequest?,
    onDismiss: () -> Unit
) {
    val c = Brutal.colors
    val shown = rememberLast(request)
    var text by remember(shown) { mutableStateOf(shown?.initial ?: "") }
    BrutalSheet(visible = request != null, onDismiss = onDismiss) {
        SheetHeader(shown?.title ?: "Name", onDismiss, "Close")
        BrutalTextField(
            value = text,
            onValueChange = { if (it.length <= 40) text = it },
            placeholder = "Name",
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalButton(
                text = "Cancel",
                onClick = onDismiss,
                fill = c.surface,
                modifier = Modifier.weight(1f)
            )
            BrutalButton(
                text = shown?.confirmLabel ?: "OK",
                onClick = {
                    shown?.onConfirm?.invoke(text.trim())
                    onDismiss()
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
