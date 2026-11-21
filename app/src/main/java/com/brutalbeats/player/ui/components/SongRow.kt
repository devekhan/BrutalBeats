package com.brutalbeats.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.readable

/** 3:45 or 1:02:09. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A 48dp tap target around a bare icon (heart, more, close...). */
@Composable
fun IconTap(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BrutalIcon(glyph, tint = tint)
    }
}

/** One song in a list. Tap plays, long-press (or the dots) opens the menu. The current song is highlighted. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Brutal.colors
    val shape = RoundedCornerShape(Brutal.dims.corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        isCurrent -> c.accent
        pressed -> c.accent.copy(alpha = 0.35f).compositeOver(c.surface)
        else -> c.surface
    }
    val fg = fill.readable()

    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .brutalBox(fill, shape, Brutal.dims.border, c.ink, 4.dp)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onMore,
                    onClick = onClick
                )
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AlbumArt(
                uri = song.uri,
                albumId = song.albumId,
                modifier = Modifier.size(48.dp),
                sizePx = 128,
                shadow = 2.dp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = BrutalType.bodyBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} · ${formatDuration(song.durationMs)}",
                    style = BrutalType.caption,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconTap(
                glyph = if (isFavorite) Glyph.HeartFilled else Glyph.Heart,
                contentDescription = if (isFavorite) "Remove ${song.title} from favorites" else "Add ${song.title} to favorites",
                onClick = onToggleFavorite,
                tint = if (isFavorite) c.accent2 else fg
            )
            IconTap(
                glyph = Glyph.More,
                contentDescription = "More options for ${song.title}",
                onClick = onMore,
                tint = fg
            )
        }
    }
}
