package com.brutalbeats.player.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSheet
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.IconTap
import com.brutalbeats.player.ui.components.hardShadow
import com.brutalbeats.player.ui.nav.QueueEntry
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.readable
import kotlin.math.roundToInt

private val ROW_HEIGHT = 60.dp
private val ROW_GAP = 8.dp

/** The play queue: tap to jump, drag the handle to reorder, X to remove. */
@Composable
fun BoxScope.QueueSheet(
    visible: Boolean,
    queue: List<QueueEntry>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val c = Brutal.colors
    val listState = rememberLazyListState()
    LaunchedEffect(visible) {
        if (visible && queue.isNotEmpty()) {
            listState.scrollToItem((currentIndex - 2).coerceIn(0, queue.lastIndex))
        }
    }

    BrutalSheet(visible = visible, onDismiss = onDismiss, scrollable = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BrutalHeading(text = "Queue", style = BrutalType.h2)
                Text(
                    text = "${queue.size} songs",
                    style = BrutalType.caption,
                    color = c.textMuted
                )
            }
            BrutalIconButton(onClick = onDismiss, contentDescription = "Close queue", fill = c.surface) {
                BrutalIcon(Glyph.Close)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (queue.isEmpty()) {
            Text(
                text = "Nothing queued yet. Play a song and it shows up here.",
                style = BrutalType.body,
                color = c.textMuted
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                items(queue, key = { it.index }) { entry ->
                    QueueRow(
                        entry = entry,
                        isCurrent = entry.index == currentIndex,
                        lastIndex = queue.lastIndex,
                        onJump = { onJump(entry.index) },
                        onRemove = { onRemove(entry.index) },
                        onMove = onMove
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    lastIndex: Int,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val c = Brutal.colors
    val shape = RoundedCornerShape(Brutal.dims.corner)
    val fill = if (isCurrent) c.accent else c.surface
    val fg = fill.readable()
    val itemHeightPx = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val song = entry.song

    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT + ROW_GAP)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragY }
            .padding(end = 6.dp, bottom = ROW_GAP)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .hardShadow(3.dp, c.ink, shape)
                .clip(shape)
                .background(fill)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onJump
                )
                .border(Brutal.dims.border, c.ink, shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { this.contentDescription = "Drag to reorder" }
                    .pointerInput(entry.index, lastIndex) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                val steps = (dragY / itemHeightPx).roundToInt()
                                dragging = false
                                dragY = 0f
                                if (steps != 0) onMove(entry.index, (entry.index + steps).coerceIn(0, lastIndex))
                            },
                            onDragCancel = {
                                dragging = false
                                dragY = 0f
                            }
                        ) { change, amount ->
                            change.consume()
                            dragY += amount.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                BrutalIcon(Glyph.Sort, tint = fg)
            }
            if (song != null) {
                AlbumArt(
                    uri = song.uri,
                    albumId = song.albumId,
                    modifier = Modifier.size(40.dp),
                    sizePx = 96,
                    shadow = 0.dp
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "Unavailable file",
                    style = BrutalType.bodyBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "It may have been deleted",
                    style = BrutalType.caption,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconTap(Glyph.Close, "Remove from queue", onRemove, tint = fg)
        }
    }
}
