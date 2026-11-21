package com.brutalbeats.player.ui.screens.player

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.output.OutputViewModel
import com.brutalbeats.player.playback.SleepState
import com.brutalbeats.player.ui.components.ActiveOutputChip
import com.brutalbeats.player.ui.components.AlbumArt
import com.brutalbeats.player.ui.components.ArtLoader
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSlider
import com.brutalbeats.player.ui.components.GlassPanel
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LocalGlassState
import com.brutalbeats.player.ui.components.Visualizer
import com.brutalbeats.player.ui.components.formatDuration
import com.brutalbeats.player.ui.components.glassSource
import com.brutalbeats.player.ui.components.rememberGlassState
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.nav.SheetHost
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.LocalGlassIntensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full-screen player. Slides up with a spring over the app. Blurred album-art background,
 * big swipeable art (swipe left/right for next/previous), glass control panel, animated bars.
 * Place it in the root Box AFTER the screens and mini player; it draws its own sheets on top.
 */
@Composable
fun BoxScope.NowPlayingScreen(
    visible: Boolean,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        NowPlayingContent(onClose = onClose, onOpenEqualizer = onOpenEqualizer)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x"
    else String.format(Locale.US, "%.2f", speed).trimEnd('0') + "x"

@Composable
private fun NowPlayingContent(
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
    outputVm: OutputViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val sheets = LocalSheets.current
    val song by vm.song.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val sleep by vm.sleepState.collectAsStateWithLifecycle()
    val output by outputVm.state.collectAsStateWithLifecycle()
    val glass = rememberGlassState()
    val intensity = LocalGlassIntensity.current

    var art by remember { mutableStateOf<Bitmap?>(null) }
    var accentTarget by remember { mutableStateOf(c.accent) }
    LaunchedEffect(art, c.accent) {
        val bitmap = art
        accentTarget = if (bitmap != null) ArtLoader.accentOf(bitmap, c.accent) else c.accent
    }
    val accent by animateColorAsState(accentTarget, label = "nowPlayingAccent")

    CompositionLocalProvider(LocalGlassState provides glass) {
        Box(Modifier.fillMaxSize()) {
            // The blurred background. This is the "source" the glass panels frost.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.background)
                    .glassSource(glass, intensity)
            ) {
                val bitmap = art
                if (bitmap != null) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier)
                    )
                }
                val overlayAlpha = when {
                    bitmap == null -> 1f
                    Build.VERSION.SDK_INT >= 31 -> 0.35f
                    else -> 0.7f // no real blur on old Android: tint it more instead
                }
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = overlayAlpha)))
            }

            val current = song
            if (current == null) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "NOTHING PLAYING",
                        style = BrutalType.h2,
                        color = c.text,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    BrutalIconButton(onClick = onClose, contentDescription = "Close player", fill = c.surface) {
                        BrutalIcon(Glyph.ChevronDown)
                    }
                }
            } else {
                PlayerBody(
                    song = current,
                    isFavorite = current.id in favoriteIds,
                    state = state,
                    accent = accent,
                    sleepActive = sleep !is SleepState.Off,
                    vm = vm,
                    outputActive = output.active,
                    onArtLoaded = { art = it },
                    onClose = onClose,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenOutput = { sheets.showOutput = true },
                    onOpenQueue = { sheets.showQueue = true },
                    onOpenSleep = { sheets.showSleep = true }
                )
            }

            // Sheets opened from here must frost THIS screen, so they are drawn inside it.
            SheetHost()
        }
    }
}

@Composable
private fun PlayerBody(
    song: Song,
    isFavorite: Boolean,
    state: com.brutalbeats.player.playback.PlayerUiState,
    accent: Color,
    sleepActive: Boolean,
    vm: PlayerViewModel,
    outputActive: com.brutalbeats.player.output.OutputDevice?,
    onArtLoaded: (Bitmap?) -> Unit,
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleep: () -> Unit
) {
    val c = Brutal.colors
    val positionFlow = remember { vm.positionFlow() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        val artSize = minOf(maxWidth - 8.dp, maxHeight * 0.34f).coerceAtLeast(120.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- top bar
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrutalIconButton(onClick = onClose, contentDescription = "Close player", fill = c.surface) {
                    BrutalIcon(Glyph.ChevronDown)
                }
                Spacer(Modifier.weight(1f))
                BrutalChip(
                    text = formatSpeed(state.speed),
                    selected = state.speed != 1f,
                    onClick = { vm.cycleSpeed(state.speed) },
                    selectedFill = accent
                )
                Spacer(Modifier.size(12.dp))
                BrutalIconButton(onClick = onOpenQueue, contentDescription = "Open queue", fill = c.surface) {
                    BrutalIcon(Glyph.Queue)
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- swipeable art
            SwipeableArt(
                song = song,
                size = artSize,
                onLoaded = onArtLoaded,
                onNext = vm::next,
                onPrevious = vm::previous
            )
            Spacer(Modifier.height(20.dp))

            // ---- glass control panel
            GlassPanel(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = song.title,
                        style = BrutalType.h2,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} · ${song.album}",
                        style = BrutalType.body,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Visualizer(isPlaying = state.isPlaying, color = accent)
                    Spacer(Modifier.height(4.dp))
                    SeekSection(
                        positionFlow = positionFlow,
                        durationMs = if (state.durationMs > 0) state.durationMs else song.durationMs,
                        accent = accent,
                        onSeek = vm::seekTo
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrutalIconButton(
                            onClick = vm::toggleShuffle,
                            contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                            fill = if (state.shuffle) accent else c.surface,
                            size = 48.dp
                        ) { BrutalIcon(Glyph.Shuffle) }
                        BrutalIconButton(
                            onClick = vm::previous,
                            contentDescription = "Previous song",
                            fill = c.surface
                        ) { BrutalIcon(Glyph.Previous) }
                        BrutalIconButton(
                            onClick = vm::togglePlayPause,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            round = true,
                            size = 72.dp,
                            shadow = 5.dp,
                            fill = accent
                        ) { BrutalIcon(if (state.isPlaying) Glyph.Pause else Glyph.Play, size = 34.dp) }
                        BrutalIconButton(
                            onClick = vm::next,
                            contentDescription = "Next song",
                            fill = c.surface
                        ) { BrutalIcon(Glyph.Next) }
                        BrutalIconButton(
                            onClick = vm::cycleRepeat,
                            contentDescription = when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                else -> "Repeat off"
                            },
                            fill = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else c.surface,
                            size = 48.dp
                        ) {
                            BrutalIcon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Glyph.RepeatOne else Glyph.Repeat
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActiveOutputChip(active = outputActive, onClick = onOpenOutput, compact = true)
                        BrutalIconButton(
                            onClick = onOpenEqualizer,
                            contentDescription = "Open equalizer",
                            fill = c.accent4,
                            size = 48.dp
                        ) { BrutalIcon(Glyph.Equalizer) }
                        BrutalIconButton(
                            onClick = onOpenSleep,
                            contentDescription = if (sleepActive) "Sleep timer is on" else "Sleep timer",
                            fill = if (sleepActive) c.accent3 else c.surface,
                            size = 48.dp
                        ) { BrutalIcon(Glyph.Timer) }
                        BrutalIconButton(
                            onClick = vm::toggleFavorite,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            fill = if (isFavorite) c.accent2 else c.surface,
                            size = 48.dp
                        ) { BrutalIcon(if (isFavorite) Glyph.HeartFilled else Glyph.Heart) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Big album art. Drag left for next, right for previous; it springs back to the middle. */
@Composable
private fun SwipeableArt(
    song: Song,
    size: androidx.compose.ui.unit.Dp,
    onLoaded: (Bitmap?) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    AlbumArt(
        uri = song.uri,
        albumId = song.albumId,
        modifier = Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .size(size)
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 40f).coerceIn(-8f, 8f)
            }
            .pointerInput(song.id) {
                val threshold = 90.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        scope.launch {
                            if (x > threshold) onPrevious() else if (x < -threshold) onNext()
                            offsetX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f))
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 0.6f)) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            },
        sizePx = 640,
        shadow = 8.dp,
        contentDescription = "Album art for ${song.title}. Swipe left or right to change song.",
        onLoaded = onLoaded
    )
}

/** Seek bar + times. Only this piece redraws as the song plays. */
@Composable
private fun SeekSection(
    positionFlow: Flow<Long>,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit
) {
    val c = Brutal.colors
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val shown = dragging ?: position.coerceIn(0L, duration).toFloat()

    BrutalSlider(
        value = shown,
        onValueChange = { dragging = it },
        valueRange = 0f..duration.toFloat(),
        onValueChangeFinished = {
            dragging?.let { onSeek(it.toLong()) }
            dragging = null
        },
        fill = accent,
        enabled = durationMs > 0
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration(shown.toLong()), style = BrutalType.caption, color = c.textMuted)
        Spacer(Modifier.weight(1f))
        Text(formatDuration(durationMs), style = BrutalType.caption, color = c.textMuted)
    }
}
