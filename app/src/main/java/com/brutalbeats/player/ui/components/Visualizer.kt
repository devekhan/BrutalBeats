package com.brutalbeats.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.ui.theme.Brutal
import kotlinx.coroutines.isActive
import kotlin.math.sin
import androidx.compose.runtime.withFrameNanos as frameNanos

/**
 * Simple animated bars. Battery friendly: it only animates while music is playing and while it is on
 * screen, and the animation clock is read at draw time, so nothing recomposes each frame.
 * (It is a lively animation, not a true spectrum, so the app needs no microphone permission.)
 */
@Composable
fun Visualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Brutal.colors.accent,
    bars: Int = 24
) {
    val ink = Brutal.colors.ink
    var timeNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                frameNanos { timeNanos = it }
            }
        }
    }
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.08f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 120f),
        label = "visualizerAmp"
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val t = timeNanos / 1_000_000_000f
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val outline = 2.dp.toPx()
        for (i in 0 until bars) {
            val phase = i * 0.55f
            val wave = (sin(t * 3.1f + phase) * 0.5f + 0.5f) * 0.55f +
                (sin(t * 5.3f + phase * 1.7f) * 0.5f + 0.5f) * 0.45f
            val h = size.height * (0.1f + 0.9f * wave * amplitude).coerceIn(0.06f, 1f)
            val topLeft = Offset(i * (barWidth + gap), size.height - h)
            drawRect(color, topLeft, Size(barWidth, h))
            drawRect(ink, topLeft, Size(barWidth, h), style = Stroke(outline))
        }
    }
}
