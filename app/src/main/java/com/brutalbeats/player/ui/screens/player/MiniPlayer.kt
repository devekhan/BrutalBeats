package com.brutalbeats.player.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.output.OutputViewModel
import com.brutalbeats.player.ui.components.ActiveOutputChip
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSurface
import com.brutalbeats.player.ui.components.GlassPanel
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import kotlinx.coroutines.delay

/**
 * Glass bar shown on every screen while a song is loaded. Tap to open Now Playing.
 * Place it as a sibling above the screen content (not inside the glassSource box).
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PlayerViewModel = hiltViewModel(),
    outputVm: OutputViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val sheets = LocalSheets.current
    val song by vm.song.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val output by outputVm.state.collectAsStateWithLifecycle()
    val shown = song

    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier,
        enter = scaleIn(
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            initialScale = 0.85f
        ) + fadeIn(),
        exit = scaleOut(targetScale = 0.9f) + fadeOut()
    ) {
        GlassPanel(Modifier.fillMaxWidth().padding(start = 12.dp, end = 18.dp)) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Open player",
                            onClick = onExpand
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AlbumArt(
                        uri = shown?.uri,
                        albumId = shown?.albumId ?: 0L,
                        modifier = Modifier.size(48.dp),
                        sizePx = 128,
                        shadow = 2.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = shown?.title ?: "",
                            style = BrutalType.bodyBold,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = shown?.artist ?: "",
                            style = BrutalType.caption,
                            color = c.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ActiveOutputChip(
                        active = output.active,
                        onClick = { sheets.showOutput = true },
                        compact = true
                    )
                    BrutalIconButton(
                        onClick = vm::togglePlayPause,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        round = true,
                        size = 48.dp,
                        shadow = 3.dp,
                        fill = c.accent
                    ) {
                        BrutalIcon(if (state.isPlaying) Glyph.Pause else Glyph.Play)
                    }
                }
                MiniProgress(vm = vm, durationMs = state.durationMs)
            }
        }
    }
}

/** Thin progress strip. Only this small piece redraws while the song plays. */
@Composable
private fun MiniProgress(vm: PlayerViewModel, durationMs: Long) {
    val c = Brutal.colors
    val positionFlow = remember { vm.positionFlow() }
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val track = c.surface
    val fill = c.accent
    val ink = c.ink
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { this.contentDescription = "Song progress" }
    ) {
        val fraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        drawRect(track, Offset.Zero, size)
        drawRect(fill, Offset.Zero, Size(size.width * fraction, size.height))
        drawRect(ink, Offset.Zero, Size(size.width, 3.dp.toPx()))
    }
}

/** A banner that appears when a file couldn't be played (it is skipped automatically) and fades after 4 seconds. */
@Composable
fun PlayerErrorBanner(
    modifier: Modifier = Modifier,
    vm: PlayerViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val error = state.error
    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            vm.clearError()
        }
    }
    AnimatedVisibility(
        visible = error != null,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut()
    ) {
        BrutalSurface(Modifier.padding(start = 16.dp, end = 22.dp, bottom = 6.dp), fill = c.danger) {
            Text(
                text = error ?: "",
                style = BrutalType.bodyBold,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
